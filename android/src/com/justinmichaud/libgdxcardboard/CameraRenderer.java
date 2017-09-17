package com.justinmichaud.libgdxcardboard;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.WindowManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.pedro.vlc.VlcListener;
import com.pedro.vlc.VlcVideoLibrary;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.opengles.GL10;

import static com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA;
import static com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA;

public class CameraRenderer {

    private final SurfaceTexture cameraPreviewTexture;
    private final int cameraTextureUnit;
    private final Mesh mesh;
    private final ShaderProgram externalShader;
    private final Camera worldCamera;
    private final VlcVideoLibrary vlcVideoLibrary;
    private HeadTransform headTransform;

    private static String externalFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "\n" +
            "uniform samplerExternalOES u_Texture;\n" +
            "varying vec2 v_TexCoord;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(u_Texture, v_TexCoord);\n" +
            "}\n";

    public CameraRenderer(final Activity activity) {
        int[] hTex = new int[1];
        GLES20.glGenTextures ( 1, hTex, 0 );
        cameraTextureUnit = hTex[0];

        cameraPreviewTexture = new SurfaceTexture(cameraTextureUnit);
        cameraPreviewTexture.setDefaultBufferSize(1920, 1080);

        vlcVideoLibrary = new VlcVideoLibrary(activity, new VlcListener() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onError() {
                System.out.println("onError!!!!!!!!!!!!!!!!!!!!");
            }
        }, cameraPreviewTexture);

        mesh = new Mesh(true, 10000, 10000, VertexAttribute.Position());
        mesh.setVertices(readFloats(R.raw.verts, activity));
        mesh.setIndices(readShort(R.raw.indices, activity));

        externalShader = new ShaderProgram(readTxt(R.raw.vertexshader, activity), externalFragmentShader);
        worldCamera = new Camera();

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                vlcVideoLibrary.play("rtsp://10.21.62.55:8080/h264_ulaw.sdp");
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private float[] readFloats(int id, Context c) {
        String[] values = readTxt(id, c).replaceAll("\n", "").split(" ");
        float[] f = new float[values.length];
        for (int i=0; i<values.length; i++) {
            f[i] = Float.parseFloat(values[i]);
        }
        return f;
    }

    private short[] readShort(int id, Context c) {
        String[] values = readTxt(id, c).replaceAll("\n", "").split(" ");
        short[] f = new short[values.length];
        for (int i=0; i<values.length; i++) {
            f[i] = (short) (Short.parseShort(values[i]) - 1);
        }
        return f;
    }

    private String readTxt(int id, Context c){
        InputStream inputStream = c.getResources().openRawResource(id);
        StringBuilder b = new StringBuilder();

        int i;
        try {
            i = inputStream.read();
            while (i != -1)
            {
                b.append((char) i);
                i = inputStream.read();
            }
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }

        return b.toString();
    }

    public void update(HeadTransform transform) {
        worldCamera.update(transform);
    }

    public void drawEye(Eye eye) {
        Gdx.gl.glEnable(GL10.GL_BLEND);
        Gdx.gl.glBlendFunc (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        //Draw camera
        Gdx.gl.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                cameraTextureUnit);
        cameraPreviewTexture.updateTexImage();

        worldCamera.updateEye(eye);

        externalShader.begin();
        externalShader.setUniformMatrix("u_proj", worldCamera.camera.combined);
        mesh.render(externalShader, GL20.GL_TRIANGLES);
        externalShader.end();
    }

    public void dispose() {
        cameraPreviewTexture.release();
    }
}
