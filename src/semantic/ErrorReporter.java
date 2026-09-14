package semantic;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

// 收集语义错误，带行号
public class ErrorReporter {

    private final List<String> errors = new ArrayList<>();

    public void error(Token token, String message) {
        errors.add("line " + token.getLine() + ": " + message);
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
