package com.dtcookie.bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dtcookie.util.Streams;

public final class SubProcess extends Thread {


    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private final boolean isInstrumented;
    private final String purpose;
    private final String clusterID;

    private Process process;

    public SubProcess(String clusterID, String purpose, boolean isInstrumented) {
        this.purpose = purpose;
        this.clusterID = clusterID;
        this.isInstrumented = isInstrumented;
    }

    public void execute() throws IOException {
        Properties envVars = new Properties();
        try (InputStream in = SubProcess.class.getClassLoader().getResourceAsStream(clusterID + ".jvm.environment.properties")) {
            if (in != null) {
                envVars.load(in);
            }            
        } catch (Throwable t) {
            t.printStackTrace();
        }
        
        ArrayList<String> jvmOptions = new ArrayList<>();

        try (InputStream in = SubProcess.class.getClassLoader().getResourceAsStream(this.clusterID + ".jvm.options")) {
            if (in != null) {
                try (InputStreamReader isr = new InputStreamReader(in)) {
                    try (BufferedReader br = new BufferedReader(isr)) {
                        String line = br.readLine();
                        while (line != null) {
                            line = line.trim();
                            if (!line.startsWith("#")) {
                                jvmOptions.add(line);
                            }
                            line = br.readLine();
                        }                        
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        String classPath = System.getProperty("java.class.path");
		System.out.println(classPath);
        String javaHome = System.getProperty("java.home");
        File javaExecutable = new File(new File(new File(javaHome), "bin"), "javaw.exe");
        if (!javaExecutable.exists()) {
            javaExecutable = new File(new File(new File(javaHome), "bin"), "java");
        }
        ArrayList<String> command = new ArrayList<String>();
        command.add(javaExecutable.getAbsolutePath());
        File tmpDir = new File(".tmp");
        tmpDir.mkdirs();        
       
        ArrayList<String> options = new ArrayList<>();
        options.addAll(jvmOptions);
        options.add("-cp");
        options.add(classPath);

        Path temp = Files.createTempFile(tmpDir.toPath(), "opts_", ".argfile");
        try (OutputStream fos = new FileOutputStream(temp.toAbsolutePath().toString())) {
            Streams.copy(String.join(" ", options).getBytes(StandardCharsets.UTF_8), fos);
        }
        
        String arg = "@"+temp.toAbsolutePath().toString();
        command.add(arg);
        command.add(BootStrap.class.getName());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(new File(".").getAbsoluteFile());
        builder.environment().put("DT_DEBUGFLAGS", "debugDisableJavaInstrumentationNative=" + (!isInstrumented));
        for (Object key : envVars.keySet()) {
            builder.environment().put(key.toString(), envVars.get(key).toString());
        }            

        builder.environment().put("DEMO_PURPOSE", purpose);        
        builder.environment().put("DT_CLUSTER_ID", clusterID);
        builder.environment().put("DT_LOCALTOVIRTUALHOSTNAME", clusterID);        

        synchronized (this) {
            this.process = builder.start();
        }

        executorService.submit(new StreamGobbler(process.getInputStream(), System.out::println));
        executorService.submit(new StreamGobbler(process.getErrorStream(), System.err::println));

        Runtime.getRuntime().addShutdownHook(this);
    }

    @Override
    public void run() {
        synchronized (this) {
            if (this.process != null) {
                this.process.destroy();
            }
        }
    }
}
