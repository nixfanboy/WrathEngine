/**
 *  Wrath Engine 
 *  Copyright (C) 2015  Trent Spears
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package wrath.client.graphics;

import java.io.File;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import wrath.client.InstanceRegistry;
import wrath.client.enums.RenderMode;
import wrath.common.Closeable;

/**
 * Class to represent a Model (both 2D and 3D).
 * @author Trent Spears
 */
public class Model implements Renderable, Closeable
{
    private static final int TEXTURE_ATTRIB_INDEX = 1;
    private static final int VERTICIES_ATTRIB_INDEX = 0;
    
    private static final HashMap<String, Model> map = new HashMap<>();
    
    /**
     * Creates a 2D or 3D model from a list of verticies.
     * Models are always assumed to be made with triangles, and will be rendered as such.
     * @param modelName The {@link java.lang.String} representation of the model's name.
     * @param verticies The list of verticies in the model. One point is represented by (x, y, z), and there must be at least 3 points.
     * @param indicies The list of points to connect for OpenGL. Look up indicies in OpenGL for reference.
     * @return Returns the {@link wrath.client.graphics.Model} object of your model.
     */
    public static Model createModel(String modelName, float[] verticies, int[] indicies)
    {
        // Generating VAO
        int vaoid = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoid);
        
        // Generating Verticies VBO
        int vtvboid = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vtvboid);
        FloatBuffer vbuffer = BufferUtils.createFloatBuffer(verticies.length);
        vbuffer.put(verticies);
        vbuffer.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vbuffer, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(VERTICIES_ATTRIB_INDEX, 3, GL11.GL_FLOAT, false, 0, 0);
        
        // Generating Indicies VBO
        int invboid = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, invboid);
        IntBuffer ibuffer = BufferUtils.createIntBuffer(indicies.length);
        ibuffer.put(indicies);
        ibuffer.flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ibuffer, GL15.GL_STATIC_DRAW);
        
        // Creating Model Object
        Model model = new Model(modelName, null, vaoid, new Integer[]{vtvboid, invboid}, indicies.length);
        map.put(modelName, model);
        InstanceRegistry.getGameInstance().getLogger().log("Loaded model '" + modelName + "'!");
        
        // Unbinding OpenGL Objects
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        InstanceRegistry.getGameInstance().addToTrashCleanup(model);
        return model;
    }
    
    /**
     * Loads a 2D or 3D model from specified {@link java.io.File}.
     * Models are always assumed to be made with triangles, and will be rendered as such.
     * @param modelFile The {@link java.io.File} to read the model data from.
     * @return Returns the {@link wrath.client.graphics.Model} object of your model.
     */
    public static Model loadModel(File modelFile)
    {
        return loadModel(modelFile.getName(), modelFile);
    }
    
    /**
     * Loads a 2D or 3D model from specified {@link java.io.File}.
     * Models are always assumed to be made with triangles, and will be rendered as such.
     * @param modelName The {@link java.lang.String} representation of the model's name.
     * @param modelFile The {@link java.io.File} to read the model data from.
     * @return Returns the {@link wrath.client.graphics.Model} object of your model.
     */
    public static Model loadModel(String modelName, File modelFile)
    {
        // Obtaining Data
        float[] verticies = new float[0];
        int[] indicies = new int[0];
        
        return createModel(modelName, verticies, indicies);
    }
 
    private final String modelName;
    private ShaderProgram shader = null;
    private Texture texture = null;
    private final int vao;
    private final ArrayList<Integer> vbos = new ArrayList<>();
    private final int vertexCount;
    
    private Model(String modelName, File modelFile, int vao, Integer[] initVbos, int vertexCount)
    {
        this.modelName = modelName;
        this.vao = vao;
        vbos.addAll(Arrays.asList(initVbos));
        this.vertexCount = vertexCount;
    }
    
    /**
     * Applies a {@link wrath.client.graphics.ShaderProgram} to the model to be called every time the model is rendered.
     * Only one can be attached at a time.
     * @param shader The {@link wrath.client.graphics.ShaderProgram} to associate with this model.
     */
    public void attachShader(ShaderProgram shader)
    {
        shader.bindAttribute(VERTICIES_ATTRIB_INDEX, "in_Position");
        if(texture != null) shader.bindAttribute(TEXTURE_ATTRIB_INDEX, "in_TextureCoord");
        this.shader = shader;
    }
    
    /**
     * Applies a {@link wrath.client.graphics.Texture} to the model to be rendered on top of the Model.
     * Only one can be attached at a time.
     * @param texture The {@link wrath.client.graphics.Texture} to associate with this model.
     */
    public void attachTexture(Texture texture)
    {
        attachTexture(texture, new float[]{0f,0f,0f,1f,1f,1f,1f,0f});
    }
    
    /**
     * Applies a {@link wrath.client.graphics.Texture} to the model to be rendered on top of the Model.
     * Only one can be attached at a time.
     * @param texture The {@link wrath.client.graphics.Texture} to associate with this model.
     * @param textureCoords
     */
    public void attachTexture(Texture texture, float[] textureCoords)
    {
        this.texture = texture;
        if(shader == null)
        {
            InstanceRegistry.getGameInstance().getLogger().log("Warning: If no shader is present to pass texture co-ordinates, then the texture will not render!");
        }
        GL30.glBindVertexArray(vao);
        int vboid = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboid);
        FloatBuffer vbuffer = BufferUtils.createFloatBuffer(textureCoords.length);
        vbuffer.put(textureCoords);
        vbuffer.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vbuffer, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(TEXTURE_ATTRIB_INDEX, 2, GL11.GL_FLOAT, false, 0, 0);
        
        if(shader != null) shader.bindAttribute(TEXTURE_ATTRIB_INDEX, "in_TextureCoord");
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        vbos.add(vboid);
    }
    
    @Override
    public void close()
    {
        if(!map.isEmpty()) map.clear();
        GL30.glDeleteVertexArrays(getVaoID());
        for(Integer i : getVboList())
                GL15.glDeleteBuffers(i);
        InstanceRegistry.getGameInstance().removeFromTrashCleanup(this);
    }
    
    /**
     * Gets the OpenGL ID of the model's Vertex Array Object.
     * @return Returns the OpenGL ID of the model's Vertex Array Object.
     */
    public int getVaoID()
    {
        return vao;
    }
    
    /**
     * Gets the OpenGL IDs of the model's Vertex Buffer Objects.
     * @return Returns the OpenGL IDs of the model's Vertex Buffer Objects.
     */
    public Integer[] getVboList()
    {
        Integer[] ret = new Integer[vbos.size()];
        vbos.toArray(ret);
        return ret;
    }
    
    /**
     * Gets the amount of verticies in the model.
     * @return Returns the number of verticies in the model.
     */
    public int getVertexCount()
    {
        return vertexCount;
    }
   
    @Override
    public void render()
    {
        GL30.glBindVertexArray(vao);
        GL20.glEnableVertexAttribArray(VERTICIES_ATTRIB_INDEX);
        if(texture != null) GL20.glEnableVertexAttribArray(TEXTURE_ATTRIB_INDEX);
        if(shader != null) shader.startUse();
        if(InstanceRegistry.getGameInstance().getRenderMode() == RenderMode.Mode2D) GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureID());
        //else GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureID()); TODO: Make 3D!
        GL11.glDrawElements(GL11.GL_TRIANGLES, vertexCount, GL11.GL_UNSIGNED_INT, 0);
        if(shader != null) shader.stopUse();
        if(texture != null) GL20.glDisableVertexAttribArray(TEXTURE_ATTRIB_INDEX);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL20.glDisableVertexAttribArray(VERTICIES_ATTRIB_INDEX);
        GL30.glBindVertexArray(0);
    }
}
