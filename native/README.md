# Optional native MIP search (C++17)

The Fabric mod stays Java 21. Only the numeric nearest-memory scan is ported to C++ via JNI. Minecraft world APIs, entities, saves, MIP parsing and RLP remain Java; calling native code for tiny calculations would usually be slower.

The Java fallback is the default and requires no native compiler. To opt in, build the shared library for your platform with a JDK 21 and CMake:

```sh
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/build --config Release
```

Start the Minecraft server with JVM flags pointing to the compiled library directory and enabling the opt-in:

```
-Djava.library.path=/absolute/path/to/native/build -Ddoppel.nativeMemory=true
```

On Windows the output is `dnms_memory.dll`; Linux `libdnms_memory.so`; macOS `libdnms_memory.dylib`. The actual output directory can vary by generator.

The native scan is used only for at least 128 integrated MIP events. It operates on a packed snapshot built when MIP index refreshes. Without the library or without opt-in, Java is used automatically. If the native library loads but fails at invocation due to an incompatible binary, the call may throw; rebuild it against your JDK. No claim of speedup is made until profiling with real MIP sizes: JNI and array pinning/copying overhead can outweigh a simple Java loop. No in-game or Gradle build verification has been completed.

The native module does not implement the novel-only real-world time reset, global memory loss, or collision semantics.
