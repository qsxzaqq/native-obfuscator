package by.radioegor146.source;

import by.radioegor146.InterfaceStaticClassProvider;
import by.radioegor146.MethodProcessor;
import by.radioegor146.NodeCache;
import by.radioegor146.Util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ClassSourceBuilder implements AutoCloseable {

    //RUST
    private final Path rsFile;
    private final BufferedWriter rsWriter;
    
    private final String className;
    private final String filename;

    private final StringPool stringPool;

    public ClassSourceBuilder(Path cppOutputDir, String className, int classIndex, StringPool stringPool) throws IOException {
        this.className = className;
        this.stringPool = stringPool;
        filename = String.format("%s_%d", Util.escapeCppNameString(className.replace('/', '_')), classIndex);

        rsFile = cppOutputDir.resolve(filename.concat(".rs"));
        rsWriter = Files.newBufferedWriter(rsFile, StandardCharsets.UTF_8);
    }

    /****
     * Class to rs文件生成
     * @param strings
     * @param classes
     * @param methods
     * @param fields
     * @throws IOException
     */
    public void addHeader(int strings, int classes, int methods, int fields) throws IOException {
//        rsWriter.append("#include \"../native_jvm.hpp\"\n");
//        rsWriter.append("#include \"../string_pool.hpp\"\n");
//        rsWriter.append("#include \"").append(getRsFilename()).append("\"\n");
//        rsWriter.append("\n");
        rsWriter.append("// ").append(Util.escapeCommentString(className)).append("\n");
        //rsWriter.append("namespace native_jvm::classes::__ngen_").append(filename).append(" {\n\n");
        //rsWriter.append("    char *string_pool;\n\n");

        if (strings > 0) {
            rsWriter.append(String.format("    jstring cstrings[%d];\n", strings));
        }
        if (classes > 0) {
            rsWriter.append(String.format("    std::mutex cclasses_mtx[%d];\n", classes));
            rsWriter.append(String.format("    jclass cclasses[%d];\n", classes));
        }
        if (methods > 0) {
            rsWriter.append(String.format("    jmethodID cmethods[%d];\n", methods));
        }
        if (fields > 0) {
            rsWriter.append(String.format("    jfieldID cfields[%d];\n", fields));
        }

        rsWriter.append("\n");
        rsWriter.append("    ");


   /*     hppWriter.append("#include \"../native_jvm.hpp\"\n");
        hppWriter.append("\n");
        hppWriter.append("#ifndef ").append(filename.concat("_hpp").toUpperCase()).append("_GUARD\n");
        hppWriter.append("\n");
        hppWriter.append("#define ").append(filename.concat("_hpp").toUpperCase()).append("_GUARD\n");
        hppWriter.append("\n");
        hppWriter.append("// ").append(Util.escapeCommentString(className)).append("\n");
        hppWriter.append("namespace native_jvm::classes::__ngen_")
                .append(filename)
                .append(" {\n\n");*/
    }

    public void addInstructions(String instructions) throws IOException {
        rsWriter.append(instructions);
        rsWriter.append("\n");
    }

    public void registerMethods(NodeCache<String> strings, NodeCache<String> classes, String nativeMethods,
                                InterfaceStaticClassProvider staticClassProvider) throws IOException {

        rsWriter.append("    void __ngen_register_methods(JNIEnv *env, jclass clazz) {\n");
        rsWriter.append("        string_pool = string_pool::get_pool();\n\n");

        for (Map.Entry<String, Integer> string : strings.getCache().entrySet()) {
            rsWriter.append("        if (jstring str = env->NewStringUTF(").append(stringPool.get(string.getKey())).append(")) { if (jstring int_str = utils::get_interned(env, str)) { ")
                    .append(String.format("cstrings[%d] = ", string.getValue()))
                    .append("(jstring) env->NewGlobalRef(int_str); env->DeleteLocalRef(str); env->DeleteLocalRef(int_str); } }\n");
        }

        if (!classes.isEmpty()) {
            rsWriter.append("\n");
        }

        if (!nativeMethods.isEmpty()) {
            rsWriter.append("        JNINativeMethod __ngen_methods[] = {\n");
            rsWriter.append(nativeMethods);
            rsWriter.append("        };\n\n");
            rsWriter.append("        if (clazz) env->RegisterNatives(clazz, __ngen_methods, sizeof(__ngen_methods) / sizeof(__ngen_methods[0]));\n");
            rsWriter.append("        if (env->ExceptionCheck()) { fprintf(stderr, \"Exception occured while registering native_jvm for %s\\n\", ")
                    .append(stringPool.get(className.replace('/', '.')))
                    .append("); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }\n");
            rsWriter.append("\n");
        }

        if (!staticClassProvider.isEmpty()) {
            rsWriter.append("        jobject classloader = utils::get_classloader_from_class(env, clazz);\n");
            rsWriter.append("        JNINativeMethod __ngen_static_iface_methods[] = {\n");
            rsWriter.append(staticClassProvider.getMethods());
            rsWriter.append("        };\n\n");
            rsWriter.append("        jclass iface_methods_clazz = utils::find_class_wo_static(env, classloader, ")
                    .append(strings.getPointer(staticClassProvider.getCurrentClassName().replace('/', '.'))).append(");\n");
            rsWriter.append("        if (iface_methods_clazz) env->RegisterNatives(iface_methods_clazz, __ngen_static_iface_methods, sizeof(__ngen_static_iface_methods) / sizeof(__ngen_static_iface_methods[0]));\n");
            rsWriter.append("        if (env->ExceptionCheck()) { fprintf(stderr, \"Exception occured while registering native_jvm for %s\\n\", ")
                    .append(stringPool.get(className.replace('/', '.')))
                    .append("); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }\n");
        }
        rsWriter.append("    }\n");
        rsWriter.append("}");


        //hppWriter.append("    void __ngen_register_methods(JNIEnv *env, jclass clazz);\n");
        //hppWriter.append("}\n\n#endif");
    }

    public String getFilename() {
        return filename;
    }

    public String getRsFilename() {
        return rsFile.getFileName().toString();
    }

    @Override
    public void close() throws IOException {
        rsWriter.close();
    }
}
