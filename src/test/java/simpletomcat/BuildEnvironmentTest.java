package simpletomcat;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildEnvironmentTest {
    /** 향후 JSP 컴파일에 필요한 Java 컴파일러가 실행 중인 JDK에 포함되어 있는지 검증한다. */
    @Test
    void fullJdkCompilerIsAvailableForFutureJspCompilation() {
        assertNotNull(ToolProvider.getSystemJavaCompiler(),
                "JSP compilation needs a full JDK with the Java compiler.");
    }
}
