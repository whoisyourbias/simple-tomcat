package simpletomcat.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import simpletomcat.coyote.Adapter;
import simpletomcat.coyote.http11.Http11Protocol;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class Http11ProtocolTest {
    private static final String GET="GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n";
    /** 프로토콜에서 조회한 Adapter가 생성 시 전달한 인스턴스와 동일한지 검증한다. */
    @Test void exposesConfiguredAdapter() throws Exception {
        Adapter adapter=(q,s)->{}; try(Http11Protocol protocol=new Http11Protocol(0,adapter)){assertSame(adapter,protocol.getAdapter());}
    }
    /** 같은 서버의 서로 다른 두 TCP 연결이 요청 경로를 Adapter에 전달하고 해당 경로를 응답 본문으로 받는지 검증한다. */
    @Test void roundTripsTwoRequestsThroughAdapter() throws Exception {
        List<String> paths=new CopyOnWriteArrayList<>();
        try(Running server=new Running((q,s)->{paths.add(q.getRequestURI());s.setBody(q.getRequestURI());})) {
            assertEquals("/hello",body(request(server,GET))); assertEquals("/second",body(request(server,GET.replace("/hello","/second"))));
            assertEquals(List.of("/hello","/second"),paths);
        }
    }
    /** 실제 TCP 연결에서 Host 없는 HTTP/1.0 GET과 본문 길이 0인 HTTP/1.1 POST가 Adapter에 도달하는지 검증한다. */
    @Test void acceptsHttp10AndBodylessPostOverTcp() throws Exception {
        try(Running server=new Running((q,s)->s.setBody(q.getMethod()))) {
            assertEquals("GET",body(request(server,"GET / HTTP/1.0\r\n\r\n")));
            assertEquals("POST",body(request(server,"POST / HTTP/1.1\r\nHost: a\r\nContent-Length: 0\r\n\r\n")));
        }
    }
    /** Host 없는 HTTP/1.1 요청은 Adapter 호출 없이 400을 반환하고, 다음 연결의 정상 요청은 200으로 처리하는지 검증한다. */
    @Test void returnsBadRequestThenServesNextConnection() throws Exception {
        var calls=new AtomicInteger(); try(Running server=new Running((q,s)->calls.incrementAndGet())) {
            assertTrue(request(server,"GET / HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400 "));
            assertEquals(0,calls.get()); assertTrue(request(server,GET).startsWith("HTTP/1.1 200 ")); assertEquals(1,calls.get());
        }
    }
    /** TCP 응답에서 한글·개행 본문을 온전히 받고 Content-Length가 UTF-8 기준 16바이트인지 검증한다. */
    @Test void returnsUtf8BodyWithCorrectLength() throws Exception {
        try(Running server=new Running((q,s)->s.setBody("안녕하세요\n"))) {
            String out=request(server,GET); assertTrue(out.contains("Content-Length: 16\r\n")); assertEquals("안녕하세요\n",body(out));
        }
    }
    /** 헤더 종료의 마지막 LF 전에는 응답·Adapter 호출이 없고, LF를 보내면 한 번 호출 후 200을 반환하는지 검증한다. */
    @Test void waitsForFinalHeaderByte() throws Exception {
        var calls=new AtomicInteger(); try(Running server=new Running((q,s)->calls.incrementAndGet());Socket socket=connect(server.port())) {
            write(socket,GET.substring(0,GET.length()-1)); socket.setSoTimeout(200);
            assertThrows(SocketTimeoutException.class,()->socket.getInputStream().read()); assertEquals(0,calls.get());
            write(socket,"\n"); socket.setSoTimeout(2000); assertTrue(read(socket).startsWith("HTTP/1.1 200 ")); assertEquals(1,calls.get());
        }
    }
    /** 헤더가 완성되기 전 EOF를 받으면 응답 없이 연결을 닫고, 다음 연결은 정상 처리하는지 검증한다. */
    @Test void ignoresIncompleteEofThenServesNextConnection() throws Exception {
        try(Running server=new Running((q,s)->{})) {
            try(Socket socket=connect(server.port())) {write(socket,GET.substring(0,GET.length()-1));socket.shutdownOutput();assertEquals("",read(socket));}
            assertTrue(request(server,GET).startsWith("HTTP/1.1 200 "));
        }
    }
    /** 프로토콜을 수락 대기 중 반복 종료해도 서버가 끝나고 사용하던 포트를 다시 바인딩할 수 있는지 검증한다. */
    @Test void closeUnblocksAcceptAndReleasesPort() throws Exception {
        try(Running server=new Running((q,s)->{})) {
            int port=server.port();server.stop();server.stop();try(ServerSocket rebound=new ServerSocket(port)){assertEquals(port,rebound.getLocalPort());}
        }
    }
    /** 불완전한 헤더를 읽으며 대기하는 서버도 프로토콜 종료 시 제한 시간 안에 실행을 끝내는지 검증한다. */
    @Test void closeUnblocksHeaderRead() throws Exception {
        try(Running server=new Running((q,s)->{}); Socket socket=connect(server.port())) {
            write(socket,"GET / HTTP/1.1\r\n");socket.setSoTimeout(200);
            assertThrows(SocketTimeoutException.class,()->socket.getInputStream().read());server.stop();
        }
    }
    /** 첫 Adapter 호출의 예외는 500 응답으로 처리하고, 같은 서버의 다음 연결은 200으로 처리하는지 검증한다. */
    @Test void continuesAfterAdapterFailure() throws Exception {
        var calls=new AtomicInteger();try(Running server=new Running((q,s)->{if(calls.incrementAndGet()==1)throw new RuntimeException("test");})) {
            assertTrue(request(server,GET).startsWith("HTTP/1.1 500 "));assertTrue(request(server,GET).startsWith("HTTP/1.1 200 "));
        }
    }
    private static String body(String out){return out.split("\r\n\r\n",2)[1];}
    private static Socket connect(int port)throws IOException {Socket socket=new Socket();socket.connect(new InetSocketAddress("127.0.0.1",port),2000);socket.setSoTimeout(2000);return socket;}
    private static void write(Socket socket,String text)throws IOException {socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();}
    private static String read(Socket socket)throws IOException {return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}
    private static String request(Running server,String text)throws IOException {try(Socket socket=connect(server.port())){write(socket,text);return read(socket);}}
    private static final class Running implements AutoCloseable {
        private final Http11Protocol protocol;
        private final ExecutorService executor=Executors.newSingleThreadExecutor();
        private final Future<?> serving;
        Running(Adapter adapter)throws IOException {protocol=new Http11Protocol(0,adapter);serving=executor.submit(()->{protocol.serve();return null;});}
        int port(){return protocol.getPort();}
        void stop()throws Exception{protocol.close();serving.get(2,TimeUnit.SECONDS);}
        public void close()throws Exception{try{stop();}finally{executor.shutdownNow();}}
    }
}
