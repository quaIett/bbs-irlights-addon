import org.anarres.cpp.*;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;

/** Offline compatibility-profile syntax check, not an Iris runtime/visual test. */
public class ShaderCompileCheck {
    static final Pattern INCLUDE=Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"[^\\n]*");
    static String expand(Path root, Path path, int depth) throws Exception {
        if(depth>40) throw new IllegalStateException("Include depth: "+path);
        String s=Files.readString(path).replace("\r\n","\n");
        Matcher m=INCLUDE.matcher(s); StringBuilder out=new StringBuilder();
        while(m.find()) {
            Path child=m.group(1).startsWith("/")?root.resolve(m.group(1).substring(1)):path.getParent().resolve(m.group(1));
            m.appendReplacement(out,Matcher.quoteReplacement(expand(root,child,depth+1)));
        }
        m.appendTail(out); return out.toString();
    }
    static String preprocess(String s) throws Exception {
        s=s.replaceAll("(?m)^\\s*#version[^\\n]*","").replaceAll("(?m)^\\s*#extension[^\\n]*","");
        for (String define : System.getProperty("irlcheck.defines", "").split(",")) {
            if (!define.isBlank()) s = "#define " + define.replace('=', ' ') + "\n" + s;
        }
        s="#define MC_VERSION 12004\n#define MC_GL_VERSION 460\n#define MC_GLSL_VERSION 460\n#define MC_OS_WINDOWS 1\n#define MC_GL_VENDOR_NVIDIA\n#define MC_GL_RENDERER_GEFORCE\n#define IS_IRIS\n#define MC_NORMAL_MAP 1\n#define MC_SPECULAR_MAP 1\n#define MC_RENDER_STAGE_CUSTOM_SKY 1\n#define MC_RENDER_STAGE_SUN 4\n#define MC_RENDER_STAGE_MOON 5\n#define MC_HAND_DEPTH 0.125\n#define MC_RENDER_QUALITY 1.0\n#define MC_SHADOW_QUALITY 1.0\n"+s;
        try(Preprocessor pp=new Preprocessor()) {
            pp.setListener(new DefaultPreprocessorListener(){
                public void handleWarning(Source src,int line,int col,String msg) {}
                public void handleError(Source src,int line,int col,String msg) throws LexerException {throw new LexerException(line+":"+col+" "+msg);}
            });
            pp.addInput(new StringLexerSource(s,true));
            StringBuilder out=new StringBuilder("#version 430 compatibility\n");
            for(Token t=pp.token();t!=null&&t.getType()!=Token.EOF;t=pp.token())out.append(t.getText());
            s=out.toString();
        }
        // Iris renames legacy sampler variables which shadow modern builtin names.
        if(s.matches("(?s).*uniform\\s+sampler\\w+\\s+texture\\s*;.*"))s=s.replaceAll("\\btexture\\b(?!\\s*\\()","irlcheck_texture");
        s=s.replaceAll("\\bvarying\\b","in").replaceAll("\\btexelFetch2D\\b","texelFetch")
            .replaceAll("\\btexture2DGradARB\\b","textureGrad");
        return s;
    }
    public static void main(String[] args) throws Exception {
        if(!glfwInit())throw new IllegalStateException("GLFW init failed");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_COMPAT_PROFILE);
        long w=glfwCreateWindow(32,32,"IRLights shader compile check",0,0);
        glfwMakeContextCurrent(w); GL.createCapabilities();
        System.out.println("GPU "+glGetString(GL_RENDERER));
        Path out=Path.of(args[2]); Files.createDirectories(out);
        List<String> summary=new ArrayList<>();int passed=0,failed=0;
        for(String line:Files.readAllLines(Path.of(args[1]))) {
            if(line.isBlank())continue;
            Path path=Path.of(args[0]).resolve(line); String key=line.replace('/','_').replace('\\','_');
            try {
                Path root=path;while(!root.getFileName().toString().equals("shaders"))root=root.getParent();
                String s=preprocess(expand(root,path,0));
                int type=line.endsWith(".fsh")?GL_FRAGMENT_SHADER:line.endsWith(".vsh")?GL_VERTEX_SHADER:GL_COMPUTE_SHADER;
                int sh=glCreateShader(type);glShaderSource(sh,s);glCompileShader(sh);
                String log=glGetShaderInfoLog(sh);boolean ok=glGetShaderi(sh,GL_COMPILE_STATUS)!=0;
                if(!ok){Files.writeString(out.resolve(key+".glsl"),s);Files.writeString(out.resolve(key+".log"),log);}
                summary.add((ok?"PASS ":"FAIL ")+line);if(ok)passed++;else failed++;
                glDeleteShader(sh);
            } catch(Exception e) {summary.add("FAIL "+line);Files.writeString(out.resolve(key+".log"),e.toString());failed++;}
        }
        Files.write(out.resolve("summary.txt"),summary);
        System.out.println("compiled="+passed+" failed="+failed+" report="+out);
        glfwDestroyWindow(w);glfwTerminate();
    }
}
