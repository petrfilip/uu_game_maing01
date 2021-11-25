package uu.game.main.game.bulanci.helper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import uu.game.main.game.common.ammo.Ammo;

public class AmmoDeserializer extends StdDeserializer<Ammo> {

  public AmmoDeserializer() {
    super((Class<?>) null);
  }

  protected AmmoDeserializer(Class<?> vc) {
    super(vc);
  }

  protected AmmoDeserializer(JavaType valueType) {
    super(valueType);
  }

  protected AmmoDeserializer(StdDeserializer<?> src) {
    super(src);
  }

  @Override
  public Ammo deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    String ammoClassName = jp.getCodec().readValue(jp, String.class);

    Ammo newInstance = null;

    for (final ClassPath.ClassInfo info : ClassPath.from(Ammo.class.getClassLoader()).getAllClasses()) {
      if (info.getName().startsWith(Ammo.class.getPackage().getName()) && info.getSimpleName().toLowerCase().equals(ammoClassName.toLowerCase())) {
        final Class<?> clazz = info.load();
        try {
          newInstance = (Ammo) clazz.getDeclaredConstructor().newInstance();
          return newInstance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
          e.printStackTrace();
        }
      }
    }

    return null;//new Item( itemName, new User(userId, null));
  }
}
