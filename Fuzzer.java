import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

public class Fuzzer {
    
    public static final List<Function<String, String>> REGISTERED_MUTATIONS = List.of(
        Fuzzer::mutation_insert_random_char,
        Fuzzer::mutation_insert_random_char_extended,
        Fuzzer::mutation_insert_existing_char
    );

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";
        String seed_folder = workingDirectory + "seeds/";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<html a=\"value\">...</html>";

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        runCommand(builder, seedInput, getMutatedInputs(seedInput, List.of(
                input -> input.replace("<html", "a"), // this is just a placeholder, mutators should not only do hard-coded string replacement
                input -> input.replace("<html", "")
        )));
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
                input -> { }
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
            return Files.newBufferedReader(path, charset).toString();
        } catch (IOException e) {
            System.exit(1);
            return "";
        }
    }

    private static Set<String> collect_seeds(String seed_folder){
        Charset charset = Charset.forName("UTF-8");
        
        Stream<Path> seed_file_paths = Stream.empty();
        try {
            seed_file_paths = Files.list(Paths.get(seed_folder));
        } catch (IOException e) {
            System.exit(1);
        }
    
        return seed_file_paths
            .map(file_path -> read_file_to_string(file_path, charset).toString()) 
            .collect(Collectors.toSet());
    }
    
    public static String mutation_insert_random_char(String input){
        // random printable ASCII character (32-126)
        char random_char = (char) (ThreadLocalRandom.current().nextInt(95)+32);
        int random_index = ThreadLocalRandom.current().nextInt(input.length()+1);
        
        StringBuilder sb = new StringBuilder(input);
        sb.insert(random_index, random_char);

        return sb.toString();
    }
    public static String mutation_insert_random_char_extended(String input){
        // random printable ASCII character (32-126)
        char random_char = (char) (ThreadLocalRandom.current().nextInt(255-128)+128);
        int random_index = ThreadLocalRandom.current().nextInt(input.length()+1);
        
        StringBuilder sb = new StringBuilder(input);
        sb.insert(random_index, random_char);

        return sb.toString();
    }
    
    public static String mutation_insert_existing_char(String input) {
        // get character
        int random_index = ThreadLocalRandom.current().nextInt(input.length());
        char random_char = input.charAt(random_index);

        // insert random_char
        StringBuilder sb = new StringBuilder(input);
        sb.insert(random_index, random_char);

        return sb.toString();
    }

    public static String mutation_repeat_char(String input) {
        return "";
    }

    public static String mutation_delete_char(String input) {
        return "";
    }
    public static String mutation_swap_char(String input) {
        return "";
    }
    
    private static String apply_mutation(String input) {
        int random_index = ThreadLocalRandom.current().nextInt(REGISTERED_MUTATIONS.size());
        Function<String, String> random_mutation = REGISTERED_MUTATIONS.get(random_index);
        return random_mutation.apply(input);
    }
}
