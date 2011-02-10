package ru.hh.search.httprpc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class ProtobufTest extends AbstractClientServerTest {

  @Override
  protected SerializerFactory serializerFactory() {
    return new ProtobufSerializerFactory();
  }
  
  @Test
  public void test() throws ExecutionException, InterruptedException {
    RPC<Messages.Request, Messages.Reply> signature = RPC.signature("/helloMethod", Messages.Request.class, Messages.Reply.class);
    ProtobufMethod serverMethod = new ProtobufMethod();
    server.register(signature, serverMethod);

    final Messages.Request argument = Messages.Request.newBuilder().setRequest("hello").build();
    
    Messages.Reply local = serverMethod.call(null, argument).get();
    
    ClientMethod<Messages.Request, Messages.Reply> clientMethod = client.createMethod(signature);
    Messages.Reply remote = clientMethod.call(address, new Envelope(10, "asdf"), argument).get();
    
    assertEquals(remote, local);
  }

  public static class ProtobufMethod implements ServerMethod<Messages.Request, Messages.Reply> {
    @Override
    public ListenableFuture<Messages.Reply> call(Envelope envelope, Messages.Request argument) {
      return Futures.immediateFuture(Messages.Reply.newBuilder().setReply(argument.getRequest().toUpperCase()).build());
    }
  }
}