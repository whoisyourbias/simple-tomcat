/**
 * Tomcat의 핵심 책임을 직접 구현하며 학습하는 프로젝트의 루트 패키지다.
 * <p>현재 네트워크 연결은 {@link simpletomcat.util.net.Endpoint}, HTTP 처리와
 * 상위 호출 경계는 {@link simpletomcat.coyote} 및
 * {@link simpletomcat.coyote.http11.Http11Processor}에서 제공한다.
 * Catalina 컨테이너와 Jasper의 JSP 처리는 후속 학습 단계다.
 */
package simpletomcat;
