import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.Source;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

// Replica of Iris 1.7.2 JcppProcessor.glslPreprocessSource for offline lexing
// validation of GLSL program sources (include-resolved beforehand).
// Usage: java GlslTest <concatenated glsl file>
// Prints nothing on success; prints the exception (with line:col) on failure.
public class GlslTest {
	public static void main(String[] args) throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);

		source = source.replace("#version", "#warning IRIS_PASSTHROUGH_VER");
		source = source.replace("#extension", "#warning IRIS_PASSTHROUGH_EXT");
		source = source.replace(String.valueOf((char) 0), "");

		try (Preprocessor pp = new Preprocessor()) {
			pp.setListener(new DefaultPreprocessorListener() {
				@Override
				public void handleWarning(Source s, int line, int column, String msg) {
					// markers + real warnings: swallow (the real listener collects markers)
				}

				@Override
				public void handleError(Source s, int line, int column, String msg) throws LexerException {
					System.out.println("[error-cb] " + line + ":" + column + " " + msg);
				}
			});
			pp.addInput(new StringLexerSource(source, true));
			pp.addFeature(Feature.KEEPCOMMENTS);

			StringBuilder builder = new StringBuilder();
			try {
				for (; ; ) {
					Token tok = pp.token();
					if (tok == null) break;
					if (tok.getType() == Token.EOF) break;
					builder.append(tok.getText());
				}
			} catch (Exception e) {
				System.out.println("[FAIL] " + e);
			}
		}
	}
}
