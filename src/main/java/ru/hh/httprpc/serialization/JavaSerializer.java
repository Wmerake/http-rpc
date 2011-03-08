package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import ru.hh.httprpc.util.FastObjectInputStream;

public class JavaSerializer implements Serializer {
  public String getContentType() {
    return "application/x-java-serialized-object";
  }

  @SuppressWarnings("unchecked")
  public <T> Function<T, ChannelBuffer> encoder(Class<T> clazz) {
    return (Function<T, ChannelBuffer>) ENCODER;
  }

  @SuppressWarnings("unchecked")
  public <T> Function<ChannelBuffer, T> decoder(Class<T> clazz) {
    return (Function<ChannelBuffer, T>) DECODER;
  }

  private static Function<Object, ChannelBuffer> ENCODER = new Function<Object, ChannelBuffer>() {
    public ChannelBuffer apply(Object object) {
      try {
        ChannelBuffer serialForm = ChannelBuffers.dynamicBuffer();
        ObjectOutputStream oos = new ObjectOutputStream(new ChannelBufferOutputStream(serialForm));
        oos.writeObject(object);
        return serialForm;
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };

  private static Function<ChannelBuffer, Object> DECODER = new Function<ChannelBuffer, Object>() {
    public Object apply(ChannelBuffer serialForm) {
      try {
        // Todo: Use JBoss Marshalling/Serialization?
        ObjectInputStream ois = new FastObjectInputStream(new ChannelBufferInputStream(serialForm));
        return ois.readObject();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };
}