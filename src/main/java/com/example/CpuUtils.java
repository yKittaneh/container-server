package com.example;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class CpuUtils {
    private static final Logger logger = Logger.getLogger(CpuUtils.class.getName());

    private static final String TASK_NAME = "org.example.TaskSimulator";

    public static List<String> CPU_LIMIT_PID_LIST = new ArrayList<>();

    public static String TASK_SIM_PID;

    public static void main(String[] args) {
        logger.info("CpuUtils.main called");

        getTaskCpuLevel();
    }

    public static void getTaskCpuLevel() {
        String command = "top -b -n 1 -o +%CPU -c -w512 | sed -n '7,15p' | awk '{printf \"%9s === %-8s === %-6s === %s ;\\n\",$1,$2,$9,$12}'";

        ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", "-c", command);
        pb.redirectError();

        StringBuilder sb = new StringBuilder();
        List<String > list = new ArrayList<>();
        char c;

        try {
            Process p = pb.start();
            InputStream is = p.getInputStream();
            int value = -1;
            while ((value = is.read()) != -1) {
                c = ((char)value);
                if (c == ';') {
                    list.add(sb.toString());
                    sb.setLength(0);
                    continue;
                }
                sb.append(c);
            }
            int exitCode = p.waitFor();
            logger.info("Top exited with " + exitCode);

//            logger.info("Output:\n" + sb);
//            for (String ss : list){
//                logger.info(ss);
//            }

            String taskCPU ;
            String[] array;
            for (String ss : list){
                array = ss.split("===");
                if (array[3].trim().contains("TaskSimulator")) {
                    taskCPU = array[2].trim();
                    System.out.println("TaskSimulator cpu = " + taskCPU);
                    break;
                }
            }

            InputStream error = p.getErrorStream();
            while ((value = error.read()) != -1) {
                System.out.print(((char)value));
            }

            p.destroyForcibly();
        } catch (IOException | InterruptedException exp) {
            throw new RuntimeException(exp);
        }
    }

    public static void handleCpuLevel(double pvPower, double batteryPower) {

        double totalRenewableEnergy = pvPower + batteryPower;
        logger.info("totalRenewableEnergy in the microgrid = " + totalRenewableEnergy);
        if (totalRenewableEnergy <= 1875) {
            logger.info("Low pvPower + batteryPower, going to limit cpu");

            // todo (high): consider taking the path of limiting cpu levels by a percentage. Less power = Less CPU?
            if (isCpuLimitProcessRunning()) {
                // there is already a cpuLimit process alive, no need to do anything. Unless... (see the todo above)
                logger.info("there is already a running cpulimit process. Not doing anything");
            } else {
                startCpuLimitProcess();
                updateCpuLimitProcessId();
            }
        } else {
            logger.info("High pvPower + batteryPower, going to release cpu");
            if (isCpuLimitProcessRunning()) {
                // kill running cpulimit process, and clear CPU_LIMIT_PID_LIST
                killCpuLimitProcess();
            }
        }
    }

    public static void startCpuLimitProcess() {
        logger.info("startCpuLimitProcess called");
//        String command = "cpulimit -p " + getTaskSimPid() + " -l 50 -b";
        String command = "./matho-primes 0 9999999999 > /dev/null &";
        runCommand(command);
        logger.info("startCpuLimitProcess finished");
    }

    public static void updateCpuLimitProcessId() {
        logger.info("getting cpu limit process id");
        try {
            ProcessBuilder builder = new ProcessBuilder();
//            builder.command("bash", "-c", "pidof cpulimit");
            builder.command("bash", "-c", "pidof matho-primes");
            Process process = builder.start();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String commandResult = bufferedReader.readLine();
            logger.info("line: " + commandResult);
            process.waitFor();
            logger.info("exit: " + process.exitValue());
            process.destroy();

            logger.info("pidof cpulimit: " + commandResult);

            if (isStringEmpty(commandResult))
                logger.warning("blank result after running command 'pidof cpulimit'");
            else
                CPU_LIMIT_PID_LIST = new ArrayList<>(Arrays.asList(commandResult.split(" ")));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error while running command [pidof cpulimit]", e);
        }
    }

    public static void killCpuLimitProcess() {
        logger.info("called killCpuLimitProcess");
        runCommand("kill -9 " + String.join(" ", CPU_LIMIT_PID_LIST));

        // send SIGCONT signal to the task sim process in case cpulimit is killed while task sim is stopped (SIGSTOP) (as part of the cpulimit operation)
//        runCommand("kill -s SIGCONT " + (TASK_SIM_PID == null ? getTaskSimPid() : TASK_SIM_PID));

        logger.info("clearing CPU_LIMIT_PID_LIST");
        CPU_LIMIT_PID_LIST.clear();
    }

    private static void runCommand(String command) {
        logger.info("runCommand: " + command);

        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("bash", "-c", command);
            Process process = builder.start();
            StreamGobbler inputStreamGobbler = new StreamGobbler(process.getInputStream(), logger::info);
            Executors.newSingleThreadExecutor().submit(inputStreamGobbler);
            StreamGobbler errorStreamGobbler = new StreamGobbler(process.getErrorStream(), logger::warning);
            Executors.newSingleThreadExecutor().submit(errorStreamGobbler);
            int exitCode = process.waitFor();
            logger.info("exitCode: " + exitCode);

            process.destroy();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error while running command [" + command + "]", e);
        }
    }

    private static boolean isCpuLimitProcessRunning() {
        return CPU_LIMIT_PID_LIST.size() > 0;
    }

    public static boolean isStringEmpty(String string) {
        return string == null || string.isEmpty();
    }

    static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }
}
