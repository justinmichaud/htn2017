package com.justinmichaud.libgdxcardboard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.opengles.GL10;

import static com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA;
import static com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA;

public class CameraRenderer {

    private android.hardware.Camera camera;
    private android.hardware.Camera.CameraInfo cameraInfo;
    private final SurfaceTexture cameraPreviewTexture;
    private final int cameraTextureUnit;
    private final Mesh mesh;
    private final ShaderProgram externalShader;
    private final Camera worldCamera;
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
        openCamera(activity);

        int[] hTex = new int[1];
        GLES20.glGenTextures ( 1, hTex, 0 );
        cameraTextureUnit = hTex[0];

        cameraPreviewTexture = new SurfaceTexture(cameraTextureUnit);
        cameraPreviewTexture.setDefaultBufferSize(500, 500);

        try {
            camera.setPreviewTexture(cameraPreviewTexture);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mesh = new Mesh(true, 10000, 10000, VertexAttribute.Position());
        mesh.setVertices(readFloats(R.raw.verts, activity));
        mesh.setIndices(readShort(R.raw.indices, activity));

        externalShader = new ShaderProgram(readTxt(R.raw.vertexshader, activity), externalFragmentShader);
        worldCamera = new Camera();
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

    public void openCamera(Activity activity) {
        while (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.CAMERA},
                    0);
        }

        camera = android.hardware.Camera.open(0);
        cameraInfo = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(0, cameraInfo);

        int targetWidth = 500;
        android.hardware.Camera.Size size = null;

        android.hardware.Camera.Parameters param = camera.getParameters();

        for (android.hardware.Camera.Size s : param.getSupportedPreviewSizes()) {
            if (size == null || Math.abs(s.width - targetWidth)
                    < Math.abs(size.width - targetWidth)) size = s;
        }
        param.setPreviewSize(size.width,size.height);
        size = null;

        for (android.hardware.Camera.Size s : param.getSupportedPictureSizes()) {
            if (size == null || Math.abs(s.width - targetWidth)
                    < Math.abs(size.width - targetWidth)) size = s;
        }
        param.setPictureSize(size.width,size.height);

        camera.setParameters(param);
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
        camera.unlock();
    }
}
