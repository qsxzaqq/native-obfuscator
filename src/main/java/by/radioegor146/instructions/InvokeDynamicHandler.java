package by.radioegor146.instructions;

import by.radioegor146.CachedMethodInfo;
import by.radioegor146.MethodContext;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Util;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InvokeDynamicHandler extends GenericInstructionHandler<InvokeDynamicInsnNode> {

    private static final Type[] PLACEHOLDER_TYPES = {
        Type.INT_TYPE,
        Type.CHAR_TYPE,
        Type.SHORT_TYPE,
        Type.BYTE_TYPE,
    };

    private static final int PLACEHOLDER_COUNT = 8;

    private static List<Type> getTypesForIndex(int index) {
        List<Type> types = new ArrayList<>();
        for (int i = 0; i < PLACEHOLDER_COUNT; i++) {
            types.add(PLACEHOLDER_TYPES[index % 4]);
            index /= 4;
        }
        return types;
    }

    private static String modifyMethodDescriptor(List<Type> prependTypes, String sourceDescriptor) {
        List<Type> resultArguments = new ArrayList<>(prependTypes);
        resultArguments.addAll(Arrays.asList(Type.getArgumentTypes(sourceDescriptor)));
        return Type.getMethodDescriptor(Type.getReturnType(sourceDescriptor), resultArguments.toArray(new Type[0]));
    }

    @Override
    protected void process(MethodContext context, InvokeDynamicInsnNode node) {
        int index = context.getInvokeDynamics().size();
        context.getInvokeDynamics().put(new NativeObfuscator.InvokeDynamicInfo(context.method.name,
                index), node);

        Type returnType = Type.getReturnType(node.desc);
        Type[] args = Type.getArgumentTypes(node.desc);
        instructionName = "INVOKESTATIC_" + returnType.getSort();

        StringBuilder argsBuilder = new StringBuilder();
        List<Integer> argOffsets = new ArrayList<>();

        int stackOffset = -1;
        for (Type argType : args) {
            argOffsets.add(stackOffset);
            stackOffset -= argType.getSize();
        }

        argsBuilder.append(", nullptr");

        for (int i = 0; i < PLACEHOLDER_COUNT; i++) {
            argsBuilder.append(", 0");
        }

        for (int i = 0; i < argOffsets.size(); i++) {
            argsBuilder.append(", ").append(context.getSnippets().getSnippet("INVOKE_ARG_" + args[i].getSort(),
                    Util.createMap("index", argOffsets.get(i))));
        }

        context.output.append(context.getSnippets().getSnippet("INVOKE_POPCNT",
                Util.createMap("count", -stackOffset - 1))).append(" ");

        props.put("class_ptr", context.getCachedClasses().getPointer(context.clazz.name));



        CachedMethodInfo methodInfo = new CachedMethodInfo(context.clazz.name, context.method.name, node.desc, true);
        int methodId = context.getCachedMethods().getId(methodInfo);
        props.put("methodid", context.getCachedMethods().getPointer(methodInfo));

        List<Type> resultArguments = new ArrayList<>();
        resultArguments.add(Type.getObjectType(context.obfuscator.getNativeDir() + "/InvokeDynamicPlaceholder"));
        resultArguments.addAll(getTypesForIndex(index));
        String resultDesc = modifyMethodDescriptor(resultArguments, node.desc);

        context.output.append(
                String.format("if (!cmethods[%d]) { cmethods[%d] = env->GetStaticMethodID(%s, %s, %s); %s  } ",
                        methodId,
                        methodId,
                        context.getCachedClasses().getPointer(context.clazz.name),
                        context.getStringPool().get(context.method.name),
                        context.getStringPool().get(resultDesc),
                        trimmedTryCatchBlock));

        props.put("args", argsBuilder.toString());
    }

    public static void processIndy(NativeObfuscator nativeObfuscator, ClassNode classNode,
                                   NativeObfuscator.InvokeDynamicInfo invokeDynamicInfo, InvokeDynamicInsnNode indy) {
        List<Type> resultArguments = new ArrayList<>();
        resultArguments.add(Type.getObjectType(nativeObfuscator.getNativeDir() + "/InvokeDynamicPlaceholder"));
        resultArguments.addAll(getTypesForIndex(invokeDynamicInfo.getIndex()));

        MethodNode indyWrapper = new MethodNode(Opcodes.ASM7,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC |
                        Opcodes.ACC_STATIC,
                invokeDynamicInfo.getMethodName(), modifyMethodDescriptor(resultArguments, indy.desc), null, new String[0]);

        int localVarsPosition = 1 + PLACEHOLDER_COUNT;
        for (Type arg : Type.getArgumentTypes(indy.desc)) {
            indyWrapper.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), localVarsPosition));
            localVarsPosition += arg.getSize();
        }

        indyWrapper.instructions.add(new InvokeDynamicInsnNode(indy.name, indy.desc, indy.bsm, indy.bsmArgs));
        indyWrapper.instructions.add(new InsnNode(Type.getReturnType(indy.desc).getOpcode(Opcodes.IRETURN)));
        classNode.methods.add(indyWrapper);
    }
}
