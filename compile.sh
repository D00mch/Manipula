#! /bin/sh
mkdir -p META-INF/native-image
cp target/manipula*.jar manipula.jar

echo '[
  { 
    "name": "java.lang.reflect.AccessibleObject",
    "methods" : [{"name":"canAccess"}]
  }
  ]' | tee META-INF/native-image/logging.json

# remove -H:+StaticExecutableWithDynamicLibC for m1 builds

native-image -jar manipula.jar --no-fallback \
    -Djava.awt.headless=false
    -J-Dclojure.spec.skip.macros=true -J-Dclojure.compiler.direct-linking=true \
    --verbose --no-server -J-Xmx3G \
    --report-unsupported-elements-at-runtime --native-image-info \
    # -H:+StaticExecutableWithDynamicLibC \
    -H:CCompilerOption=-pipe \
    -H:ReflectionConfigurationFiles=META-INF/native-image/logging.json

chmod +x manipula
echo "Size of generated native-image `ls -sh manipula-native`"
