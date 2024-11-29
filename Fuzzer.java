import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ThreadLocalRandom;

public class Fuzzer {
    
    public static final List<Function<String, String>> REGISTERED_MUTATIONS = List.of(
        Fuzzer::mutation_insert_random_char,
        Fuzzer::mutation_insert_random_char_extended,
        Fuzzer::mutation_insert_existing_char,
        Fuzzer::mutation_delete_char,
        Fuzzer::mutation_repeat_char
    );

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";
        String seed_folder = workingDirectory + "seeds/";
        Set<String> seeds = collect_seeds(seed_folder);

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        run(seeds.stream(), builder);
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // redirect stderr to stdout
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
            input -> {}
        );
    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }
    
    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String, String>> mutators) {
        return List.of();
    }
    
    private static String read_file_to_string(Path path, Charset charset) {
        try {
            return Files.newBufferedReader(path, charset).lines()
                .collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            System.exit(2);
            return "";
        }
    }

    private static Set<String> collect_seeds(String seed_folder){
        Charset charset = Charset.forName("UTF-8");
        
        Stream<Path> seed_file_paths = Stream.empty();
        try {
            seed_file_paths = Files.list(Paths.get(seed_folder));
        } catch (IOException e) {
            System.exit(3);
        }
    
        return seed_file_paths
            .map(file_path -> read_file_to_string(file_path, charset).toString()) 
            .collect(Collectors.toSet());
    }
    
    public static String mutation_insert_random_char(String input){
        // random printable ASCII character (32-126)
        char random_char = (char) (ThreadLocalRandom.current().nextInt(95)+32);
        int random_index = ThreadLocalRandom.current().nextInt(input.length());
        
        StringBuilder sb = new StringBuilder(input);
        sb.insert(random_index, random_char);

        return sb.toString();
    }
    public static String mutation_insert_random_char_extended(String input){
        // random printable ASCII extended character (128-255)
        char random_char = (char) (ThreadLocalRandom.current().nextInt(255-128)+128);
        int random_index = ThreadLocalRandom.current().nextInt(input.length());
        
        StringBuilder sb = new StringBuilder(input);
        sb.insert(random_index, random_char);

        return sb.toString();
    }
    
    public static String mutation_insert_existing_char(String input) {
        // get character
        int random_src_index = ThreadLocalRandom.current().nextInt(input.length());
        char random_char = input.charAt(random_src_index);

        // insert random_char
        int random_target_index = ThreadLocalRandom.current().nextInt(input.length());
        StringBuilder sb = new StringBuilder(input);
        sb.insert(random_target_index, random_char);

        return sb.toString();
    }

    public static String mutation_repeat_char(String input) {
        // get character
        int random_index = ThreadLocalRandom.current().nextInt(input.length());
        char random_char = input.charAt(random_index);

        StringBuilder sb = new StringBuilder(input);
        sb.insert(random_index, random_char);

        return sb.toString();
    }

    public static String mutation_delete_char(String input) {
        // get character
        int random_index = ThreadLocalRandom.current().nextInt(input.length());

        StringBuilder sb = new StringBuilder(input);
        sb.deleteCharAt(random_index);

        return sb.toString();
    }
    
    private static String apply_mutation(String input) {
        int random_index = ThreadLocalRandom.current().nextInt(REGISTERED_MUTATIONS.size());
        Function<String, String> random_mutation = REGISTERED_MUTATIONS.get(random_index);
        return random_mutation.apply(input);
    }
    
    private static void execute_fuzz(String input, ProcessBuilder command_builder) {
        try {
            Process running_process = command_builder.start();
            
            OutputStream outputStream = running_process.getOutputStream();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                writer.write(input); // Send input to the process
                writer.flush(); // Ensure the input is sent
            }
            
            try {
                int exitCode = running_process.waitFor();
                if (exitCode != 0) {
                    System.out.println(input);
                    System.exit(0);
                }
            } catch (InterruptedException e) {
                System.exit(5);
            }

            String current_mutation = input;
            String last_mutation = input;
            for (int i = 0; i < 999; i++ ) {
                running_process = command_builder.start();
                last_mutation = current_mutation;
                current_mutation = apply_mutation(current_mutation);

                outputStream = running_process.getOutputStream();
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                    writer.write(current_mutation); // Send input to the process
                    writer.flush(); // Ensure the input is sent
                }
                try {
                    int exitCode = running_process.waitFor();
                    if (exitCode != 0) {
                        System.out.println("last working mutation:\n\n" + last_mutation);
                        System.out.println("=============");
                        System.out.println("error producing mutation:\n\n" + current_mutation);
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    System.exit(5);
                }
            }
        } catch (IOException e) {
            System.exit(4);
        } 
    }
    
    private static void run(Stream<String> seeds, ProcessBuilder command_builder) {
        seeds.forEach(seed -> execute_fuzz(seed, command_builder));
        
        System.out.println("Finished operation without finding invalid inputs.");
        System.exit(0);
    }
}
