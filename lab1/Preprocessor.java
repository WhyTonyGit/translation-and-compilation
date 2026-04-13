import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Preprocessor {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Preprocessor <source file>");
            return;
        }

        String source = new String(Files.readAllBytes(Paths.get(args[0])));

        Pattern unclosedComment = Pattern.compile("/\\*(?:(?!\\*/).)*$", Pattern.DOTALL);
        Matcher unclosedMatcher = unclosedComment.matcher(source);
        if (unclosedMatcher.find()) {
            System.err.println("Error: unclosed multi-line comment detected");
            return;
        }

        Pattern forbidden = Pattern.compile("[^\\x09\\x0A\\x0D\\x20-\\x7E\\u0400-\\u04FF]");
        Matcher forbiddenMatcher = forbidden.matcher(source);
        if (forbiddenMatcher.find()) {
            System.err.println("Error: forbidden characters detected");
            return;
        }

        System.err.println("No errors found");

        Pattern multiLineComment = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        source = multiLineComment.matcher(source).replaceAll("");

        Pattern singleLineComment = Pattern.compile("//[^\\n]*");
        source = singleLineComment.matcher(source).replaceAll("");

        String[] lines = source.split("\\n", -1);

        Pattern whitespace = Pattern.compile("^\\s+|\\s+$");

        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            String trimmed = whitespace.matcher(line).replaceAll("");
            if (!trimmed.isEmpty()) {
                output.append(trimmed).append("\n");
            }
        }

        System.out.print(output);
    }
}
