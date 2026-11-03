package simpletomcat;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildEnvironmentTest {
    @Test
    void fullJdkCompilerIsAvailableForFutureJspCompilation() {
        assertNotNull(ToolProvider.getSystemJavaCompiler(),
                "JSP compilation needs a full JDK with the Java compiler.");
    }
}
