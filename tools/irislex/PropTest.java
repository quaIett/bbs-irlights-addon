import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.PreprocessorCommand;
import org.anarres.cpp.Source;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

// Verbatim replica of Iris 1.7.2 PropertiesPreprocessor.process() for offline
// debugging of shaders.properties preprocessing.
// Usage: java PropTest <properties file> [MACRO or MACRO=VALUE ...]
public class PropTest {
	public static void main(String[] args) throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(args[0])));

		try (Preprocessor pp = new Preprocessor()) {
			for (int i = 1; i < args.length; i++) {
				String a = args[i];
				int eq = a.indexOf('=');
				if (eq >= 0) pp.addMacro(a.substring(0, eq), a.substring(eq + 1));
				else pp.addMacro(a);
			}
			System.out.println(process(pp, source));
		}
	}

	private static String process(Preprocessor preprocessor, String source) {
		preprocessor.setListener(new DefaultPreprocessorListener() {
			@Override
			public void handleError(Source source, int line, int column, String msg) throws LexerException {
				if (msg.contains("Unknown preprocessor directive")
					|| msg.contains("Preprocessor directive not a word")) {
					return;
				}
				System.err.println("[error-cb] line " + line + ":" + column + " " + msg);
			}

			@Override
			public void handleWarning(Source source, int line, int column, String msg) {
				System.err.println("[warn-cb] line " + line + ":" + column + " " + msg);
			}
		});

		source = Arrays.stream(source.split("\\R")).map(String::trim).filter(s -> !s.isBlank())
			.map(line -> {
				if (line.startsWith("#")) {
					for (PreprocessorCommand command : PreprocessorCommand.values()) {
						if (line.startsWith("#" + (command.name().replace("PP_", "").toLowerCase(Locale.ROOT)))) {
							return line;
						}
					}
					return "";
				}
				return line.replace("#", "");
			}).collect(Collectors.joining("\n")) + "\n";
		source = source.replace("\\", "IRIS_PASSTHROUGHBACKSLASH");

		preprocessor.addInput(new StringLexerSource(source, true));
		preprocessor.addFeature(Feature.KEEPCOMMENTS);

		final StringBuilder builder = new StringBuilder();

		try {
			for (; ; ) {
				final Token tok = preprocessor.token();
				if (tok == null) break;
				if (tok.getType() == Token.EOF) break;
				builder.append(tok.getText());
			}
		} catch (final Exception e) {
			System.err.println("[exception] " + e);
			e.printStackTrace();
		}

		source = builder.toString();

		return source.replace("IRIS_PASSTHROUGHBACKSLASH", "\\");
	}
}
