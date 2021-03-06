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
package wrath.client;

import wrath.client.enums.WindowState;
import wrath.client.enums.RenderMode;
import wrath.client.input.InputManager;
import wrath.client.events.GameEventHandler;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALContext;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vector.Matrix4f;
import wrath.client.enums.ImageFormat;
import wrath.client.events.InputEventHandler;
import wrath.client.graphics.Camera;
import wrath.client.graphics.Color;
import wrath.client.graphics.EntityRenderer;
import wrath.client.graphics.Model;
import wrath.client.graphics.ShaderProgram;
import wrath.client.graphics.TextRenderer;
import wrath.client.graphics.TileRenderer;
import wrath.common.Closeable;
import wrath.common.Reloadable;
import wrath.common.entities.Player;
import wrath.common.javaloader.JarLoader;
import wrath.common.scheduler.Scheduler;
import wrath.common.scripts.ScriptManager;
import wrath.util.Config;
import wrath.util.Logger;

/**
 * The entry point and base of the game. Make a class extending this and overriding at least render() method.
 * @author Trent Spears
 */
public class Game 
{
    private static Game GAME_INSTANCE = null;
    
    /**
     * Gets the current, primary {@link wrath.client.Game} instance.
     * @return Returns the current, primary {@link wrath.client.Game} instance.
     */
    public static Game getCurrentInstance()
    {
        return GAME_INSTANCE;
    }
    
    private final RenderMode MODE;
    private final String TITLE;
    private final double TPS;
    private final String VERSION;

    private final Config gameConfig = new Config(new File("etc/configs/game.cfg"));
    private final Logger gameLogger = new Logger(new File("etc/logs/game.log"));
    private final Scheduler gameScheduler = new Scheduler();
    
    private GLFWErrorCallback errStr;
    private GLFWFramebufferSizeCallback winSizeStr;
    
    private ALContext audiocontext;
    private boolean isRunning = false;
    
    private final EventManager evManager;
    private final InputManager inpManager;
    private final RenderManager renManager;
    private final WindowManager winManager;
    
    private final RefreshManager refresher;
    private final TrashCollector trashCollector;
    
    private final Player player;
    private final Camera playerCamera;
    
    /**
     * Constructor.
     * Describes all the essential and unmodifiable variables of the Game.
     * @param gameTitle Title of the Game.
     * @param version Version of the Game.
     * @param ticksPerSecond The amount of times the printlnic of the game should update in one second. Recommended 30-60.
     * @param renderMode Describes how to game should be rendered (2D or 3D).
     */
    public Game(String gameTitle, String version, double ticksPerSecond, RenderMode renderMode)
    {
        MODE = renderMode;
        TITLE = gameTitle;
        VERSION = version;
        TPS = ticksPerSecond;
        duringConstructor();
        this.refresher = new RefreshManager();
        this.trashCollector = new TrashCollector();
        this.player = new Player();
        this.playerCamera = new Camera(player);
        this.evManager = new EventManager();
        this.inpManager = new InputManager();
        this.renManager = new RenderManager();
        this.winManager = new WindowManager();
        
        File nativeDir = new File("assets/native");
        if(!nativeDir.exists())
        {
            ClientUtils.throwInternalError("Missing assets folder! Try re-downloading!", true);
            stopImpl();
        }
    }

    /**
    * Adds a {@link wrath.common.Reloadable} object to be run after the window opens.
    * Note that the window opening is NOT the start of the program.
    * @param obj The {@link wrath.common.Reloadable} object to run after the window opens.
    */
    public void addToRefreshList(Reloadable obj)
    {
        refresher.list.add(obj);
    }
    
    /**
    * Adds a {@link wrath.common.Closeable} object to be run after the window closes.
    * Note that the window closing is NOT the end of the program.
    * @param obj The {@link wrath.common.Closeable} object to run after the window closes.
    */
    public void addToTrashCleanup(Closeable obj)
    {
        trashCollector.list.add(obj);
    }
    
    private void duringConstructor()
    {
        GAME_INSTANCE = this;
    }
    
    /**
     * Gets the {@link wrath.util.Config} object of the game.
     * @return Returns the configuration object of the game.
     */
    public Config getConfig()
    {
        return gameConfig;
    }
    
    /**
     * Gets the {@link wrath.client.Game.EventManager} class that manages all event handlers.
     * This class is used to control, access and change Event Handlers from the {@link wrath.client.events} package.
     * @return Returns the {@link wrath.client.Game.EventManager} class that manages all event handlers.
     */
    public EventManager getEventManager()
    {
        return evManager;
    }
    
    /**
     * Gets the {@link wrath.client.input.InputManager} linked to this {@link wrath.client.Game} instance.
     * @return Returns the {@link wrath.client.input.InputManager} linked to this {@link wrath.client.Game} instance.
     */
    public InputManager getInputManager()
    {
        return inpManager;
    }
    
    /**
     * Gets the standard info {@link wrath.util.Logger} for the game.
     * @return Returns the standard info {@link wrath.util.Logger} for the game.
     */
    public Logger getLogger()
    {
        return gameLogger;
    }
    
    /**
     * Gets the {@link wrath.common.entities.Player} object representing the player!
     * @return Returns the {@link wrath.common.entities.Player} object representing the player!
     */
    public Player getPlayer()
    {
        return player;
    }
    
    /**
     * Gets the {@link wrath.client.graphics.Camera} associated with the player.
     * @return Returns the {@link wrath.client.graphics.Camera} associated with the player.
     */
    public Camera getPlayerCamera()
    {
        return playerCamera;
    }
    
    /**
     * Gets the renderer (as specified by the {@link wrath.client.Game.RenderManager} class) for this game.
     * @return Returns the renderer (as specified by the {@link wrath.client.Game.RenderManager} class) for this game.
     */
    public RenderManager getRenderer()
    {
        return renManager;
    }
    
    /**
     * Gets whether the game should be rendered in 2D or 3D.
     * @return Returns the enumerator-style representation of the game's rendering mode.
     */
    public RenderMode getRenderMode()
    {
        return MODE;
    }
    
    /**
     * Gets the standardized {@link wrath.common.scheduler.Scheduler} for the game.
     * @return Returns the scheduler for the game.
     */
    public Scheduler getScheduler()
    {
        return gameScheduler;
    }
    
    /**
     * Gets the title/name of the Game.
     * @return Returns the title of the Game.
     */
    public String getTitle()
    {
        return TITLE;
    }
    
    /**
     * Gets the amount of times the game's printlnic will update in one second.
     * Recommended to not be over 64 or under 10.
     * If the TPS is set over 60 and VSync is on, the ticks will be forced to 60 TPS.
     * Unfortunately, there are not any good ways to overcome said bug, though I am looking into potential solutions.
     * @return Returns the Ticks-per-second of the game's printlnic.
     */
    public double getTPS()
    {
        return TPS;
    }
    
    /**
     * Gets the {@link java.lang.String} representation of the Game's Version.
     * @return Returns the version of the game in a {@link java.lang.String} format.
     */
    public String getVersion()
    {
        return VERSION;
    }
    
    /**
     * Gets the {@link wrath.client.Game.WindowManager} linked to this {@link wrath.client.Game} instance.
     * @return Returns the {@link wrath.client.Game.WindowManager} linked to this {@link wrath.client.Game} instance.
     */
    public WindowManager getWindowManager()
    {
        return winManager;
    }
    
    /**
     * Returns whether or not the game is currently running.
     * @return Returns true if the game is running, otherwise false.
     */
    public boolean isRunnning()
    {
        return isRunning;
    }
    
    /**
     * Loads a java plugin from the specified file.
     * @param jarFile The {@link java.io.File} to load the plugin from.
     * @return Returns the object formed from the Java plugin.
     */
    public Object loadJavaPlugin(File jarFile)
    {
        Object obj = JarLoader.loadObject(jarFile);
        this.getEventManager().getGameEventHandler().onLoadJavaPlugin(obj);
        return obj;
    }
    
    /**
     * Private loop (main game loop).
     */
    private void loop()
    {
        // FPS counter.
        int afpsCount = 0;
        int fpsCount = 0;
        
        //Input Checking
        int inpCount = 0;
        double checksPerSec = gameConfig.getDouble("PersistentInputChecksPerSecond", 0.0);
        if(checksPerSec > TPS || checksPerSec < 1) checksPerSec = TPS;
        final double INPUT_CHECK_TICKS = TPS / checksPerSec;
        
        //Timings
        long last = System.nanoTime();
        final double conv = 1000000000.0 / TPS;
        double delta = 0.0;
        long now;
        
        while(isRunning && (!winManager.windowOpen || GLFW.glfwWindowShouldClose(winManager.window) != GL11.GL_TRUE))
        {
            now = System.nanoTime();
            delta += (now - last) / conv;
            last = now;
            
            //Tick occurs
            while(delta >= 1)
            {
                onTickPreprocessor();
                
                //Persistent input management
                if(INPUT_CHECK_TICKS == 1 || inpCount >= INPUT_CHECK_TICKS)
                {
                    inpManager.onPersistentInput();
                    inpCount -= INPUT_CHECK_TICKS;
                }
                else inpCount++;
                
                //FPS Counter
                if(winManager.windowOpen)
                    if(fpsCount >= TPS)
                    {
                        afpsCount++;
                        renManager.fps = renManager.fpsBuf;
                        renManager.avgFps = renManager.totalFramesRendered / afpsCount;
                        renManager.fpsBuf = 0;
                        fpsCount-=TPS;
                    }
                    else fpsCount++;
                
                delta--;
            }
            
            renManager.render();
        }
        
        stop();
        stopImpl();
    }
    
    /**
     * Private method that takes care of all background processes before onTick() is called.
     */
    private void onTickPreprocessor()
    {
        gameScheduler.onTick();
        evManager.getGameEventHandler().onTick();
    }
    
    /**
     * Removes a {@link wrath.common.Reloadable} object from the refresh list.
     * @param obj The {@link wrath.common.Reloadable} object to remove from refresh list.
     */
    public void removeFromRefreshList(Reloadable obj)
    {
        refresher.list.remove(obj);
    }
    
    /**
     * Removes a {@link wrath.common.Closeable} object from the cleanup list.
     * @param obj The {@link wrath.common.Closeable} object to remove from cleanup list.
     */
    public void removeFromTrashCleanup(Closeable obj)
    {
        trashCollector.list.remove(obj);
    }
    
    /**
     * Override-able method that is called as much as possible to issue rendering commands.
     */
    protected void render(){}
    
    /**
     * Method that is used to load the game and all of it's resources.
     */
    public void start()
    {
        start(new String[0]);
    }
    
    /**
     * Method that is used to load the game and all of it's resources.
     * @param args Arguments, usually from the main method (entry point).
     */
    public void start(String[] args)
    {
        gameLogger.println("Launching '" + TITLE + "' Client v." + VERSION + "  with LWJGL v." + Version.getVersion() + "!");

        //Initialize GLFW and OpenGL
        GLFW.glfwSetErrorCallback((errStr = new GLFWErrorCallback()
        {
            @Override
            public void invoke(int error, long description) 
            {
                System.err.println("GLFW hit ERROR ID '" + error + "' with message '" + description + "'!");
            }
        }));
        
        if(GLFW.glfwInit() != GL11.GL_TRUE)
        {
            System.err.println("Could not initialize GLFW! Unknown Error!");
            ClientUtils.throwInternalError("Failed to initialize GLFW!", false);
            stopImpl();
        }
        
        //Interpret command-line arguments.
        for(String a : args)
        {
            String[] b = a.split("=", 2);
            if(b.length <= 1) continue;
            
            gameConfig.setProperty(b[0], b[1]);
            gameLogger.println("Set property '" + b[0] + "' to value '" + b[1] + "'!");
        }
        
        //Auto-loads Java Plugins from specified directory.
        if(gameConfig.getBoolean("AutoLoadJavaPlugins", true))
        {
            Object[] list = JarLoader.loadPluginsDirectory(new File(gameConfig.getString("AutoLoadJavaPluginsDirectory", "etc/plugins")));
            for(Object obj : list) evManager.getGameEventHandler().onLoadJavaPlugin(obj);
            if(list.length != 0) gameLogger.println("Loaded " + list.length + " plugins from the directory '" + gameConfig.getString("AutoLoadJavaPluginsDirectory", "etc/plugins") + "'!");
        }
        
        isRunning = true;
        winManager.openWindow();
        evManager.getGameEventHandler().onGameOpen();
        inpManager.loadKeys();
        loop();
    }
    
    /**
     * Method that flags the game to stop.
     */
    public void stop()
    {
        if(!isRunning) return;
        evManager.getGameEventHandler().onGameClose();
        isRunning = false;
    }
    
    /**
     * Method to stop the game and close all of it's resources.
     */
    private void stopImpl()
    {
        try{
        winManager.closeWindow();
        inpManager.destroyCursor();
        GLFW.glfwTerminate();
        
        gameConfig.save();
        inpManager.saveKeys();
        gameLogger.println("Average FPS throughout session: " + renManager.avgFps);
        gameLogger.println("Time of Session: " + (double)((double)(System.nanoTime() - EntryPoint.UNIX_START_TIMESTAMP)/1000/1000/1000) + " seconds.");
        gameLogger.println("Stopping '" + TITLE + "' Client v." + VERSION + "!");
        if(gameLogger != null && !gameLogger.isClosed()) gameLogger.close();
        errStr.release();
        }catch(Exception e){}
        
        ScriptManager.closeScripting();
        
        System.exit(0);
    }
    
    /**
     * Class to manage all event handlers from the {@link wrath.client.events} package.
     */
    public class EventManager
    {
        /**
         * Constructor.
         * Protected so multiple instances aren't made pointlessly.
         */
        private EventManager(){}
        
        private final ArrayList<GameEventHandler> gameHandlers = new ArrayList<>();
        private final ArrayList<InputEventHandler> inpHandlers = new ArrayList<>();
        
        private final RootGameEventHandler ghan = new RootGameEventHandler();
        private final RootInputEventHandler ihan = new RootInputEventHandler();
        
        /**
         * Adds a {@link wrath.client.events.GameEventHandler} to associate with this Game.
         * @param handler The {@link wrath.client.events.GameEventHandler} to add to the list of handlers that handles all of this Game's events.
         */
        public void addGameEventHandler(GameEventHandler handler)
        {
            gameHandlers.add(handler);
        }
        
        /**
         * Adds a {@link wrath.client.events.InputEventHandler} to associate with this Game's Input Manager.
         * @param handler The {@link wrath.client.events.InputEventHandler} to add to the list of handlers that handles all of this Game's Input events.
         */
        public void addInputEventHandler(InputEventHandler handler)
        {
            inpHandlers.add(handler);
        }
        
        /**
         * Gets the root {@link wrath.client.events.GameEventHandler} linked to this Game.
         * @return Returns the root {@link wrath.client.events.GameEventHandler} linked to this Game.
         */
        public GameEventHandler getGameEventHandler()
        {
            return ghan;
        }
        
        /**
         * Gets the root {@link wrath.client.events.InputEventHandler}s linked to this Game's Input Manager.
         * @return Returns the root {@link wrath.client.events.GameEventHandler} linked to this Game's Input Manager.
         */
        public InputEventHandler getInputEventHandler()
        {
            return ihan;
        }
    }
    
    /**
     * Class to define Graphical User Interface (GUI) of the game.
     * This *will* include method to control pop-ups, sub-windows, etc.
     */
    public class GUI
    {
        /**
         * Constructor.
         * Protected so multiple instances aren't made pointlessly.
         */
        private GUI(){}
        
        /**
         * Method to render the GUI defined by the class.
         */
        private void renderGUI()
        {
            //This is just the outline of what is to come.
        }
    }
    
    private class RefreshManager
    {
        private final ArrayList<Reloadable> list = new ArrayList<>();
        
        public void run()
        {
            list.stream().forEach((r) -> 
            {
                r.reload();
            });
        }
    }
    
    public class RenderManager
    {
        public static final float FAR_PLANE = 1000f;
        public static final float NEAR_PLANE = 0.1f;
        
        private int avgFps = 0;
        private Color color = Color.WHITE;
        private float fov = gameConfig.getFloat("FOV", 70f);
        private int fps = 0;
        private int fpsBuf = 0;
        private final GUI front = new GUI();
        private int maxFps = getConfig().getInt("MaxFps", 0);
        private Matrix4f projMatrix = new Matrix4f();
        private boolean renderFps = false;
        private TextRenderer text = null;
        private int totalFramesRendered = 0;
        
        private boolean shouldRender = true;
        private long next = 0;
        
        private final HashMap<Model, List<EntityRenderer>> entityRenderMap = new HashMap<>();
        private final HashMap<Model, List<TileRenderer>> terrainRenderMap = new HashMap<>();
        
        private RenderManager(){}
        
        /**
         * Efficiently renders an Entity.
         * @param ren The {@link wrath.client.graphics.EntityRenderer} to render.
         */
        public void addEntityRenderingJob(EntityRenderer ren)
        {
            if(entityRenderMap.containsKey(ren.getModel())) entityRenderMap.get(ren.getModel()).add(ren);
            else
            {
                ArrayList<EntityRenderer> rl = new ArrayList<>();
                rl.add(ren);
                entityRenderMap.put(ren.getModel(), rl);
            }
        }
        
        /**
         * Efficiently renders a Tile of Terrain.
         * @param ren The {@link wrath.client.graphics.TileRenderer} to render.
         */
        public void addTerrainRenderingJob(TileRenderer ren)
        {
            if(terrainRenderMap.containsKey(ren.getTileModel())) terrainRenderMap.get(ren.getTileModel()).add(ren);
            else
            {
                ArrayList<TileRenderer> rl = new ArrayList<>();
                rl.add(ren);
                terrainRenderMap.put(ren.getTileModel(), rl);
            }
        }
        
        /**
         * Gets the average FPS of the game while it has been running.
         * @return Returns the average FPS of the game while it has been running.
         */
        public double getAverageFPS()
        {
            return avgFps;
        }
    
        /**
         * Gets the defined 3D Field-of-View.
         * @return Returns the defined 3D Field-of-View.
         */
        public float getFOV()
        {
            return fov;
        }
        
        /**
        * Gets the last recorded Frames-Per-Second count.
        * @return Returns the last FPS count.
        */
        public double getFPS()
        {
            return fps;
        }
        
        /**
         * Gets the {@link wrath.client.Game.GUI} linked to this Window.
         * @return Returns the {@link wrath.client.Game.GUI} linked to this Window.
         */
        public GUI getGUI()
        {
            return front;
        }
        
        /**
         * Gets the maximum amount of times the game will render to the screen in one second.
         * @return Returns the maximum amount of times the game will render to the screen in one second.
         */
        public int getMaxFPS()
        {
            return maxFps;
        }
        
        /**
         * Gets the {@link org.lwjgl.util.vector.Matrix4f} object of the 3D projection matrix.
         * @return Returns the {@link org.lwjgl.util.vector.Matrix4f} object of the 3D projection matrix.
         */
        public Matrix4f getProjectionMatrix()
        {
            return projMatrix;
        }
        
        /**
         * Gets the standard {@link wrath.client.graphics.Color} that will be used to render unless specified otherwise by OpenGL code.
         * @return Returns the standard {@link wrath.client.graphics.Color}.
         */
        public Color getStandardColor()
        {
            return color;
        }
        
        /**
        * Gets the default global text renderer.
        * @return The current global {@link wrath.client.graphics.TextRenderer}.
        */
        public TextRenderer getTextRenderer()
        {
            return text;
        }
        
        /**
         * Gets the amount of frames the game has rendered since it launched.
         * @return Returns the amount of frames the game has rendered since it launched.
         */
        public int getTotalFramesRendered()
        {
            return totalFramesRendered;
        }
        
        /**
         * If true, the FPS will be rendered at the top-left of the screen.
         * @return Returns true if the FPS will be rendered in text at the top-left of the screen.
         */
        public boolean isRenderingFPS()
        {
            return renderFps;
        }
        
        /**
         * Method called by the loop to render everything needed.
         */
        private void render()
        {
            if(maxFps > 0)
            {
                if(next == 0 || System.nanoTime() >= next)
                {
                    if(next == 0) next = System.nanoTime();
                    next = next + (long)Math.round(((float)1.0/maxFps * 1000000000.0));
                    shouldRender = true;
                }
                else shouldRender = false;
            }
            
            if(winManager.windowOpen)
            {
                if(shouldRender)
                {
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                    color.bindColor();
                    
                    terrainRenderMap.entrySet().stream().map((entry) ->
                    {
                        Model m = (Model) entry.getKey();
                        m.renderSetup();
                        for(TileRenderer r : (List<TileRenderer>) entry.getValue())
                        {
                            r.update();
                            m.render(true);
                        }
                        return m;
                    }).forEach((m) ->
                    {
                        m.renderStop();
                    });
                    terrainRenderMap.clear();
                    
                    entityRenderMap.entrySet().stream().forEach((entry) -> {
                        Model m = (Model) entry.getKey();
                        if (!(m == null)) {
                            m.renderSetup();
                            for(EntityRenderer r : (List<EntityRenderer>) entry.getValue())
                            {
                                r.update();
                                m.render(true);
                            }
                            m.renderStop();
                        }
                    });
                    entityRenderMap.clear();
                    
                    GAME_INSTANCE.render();
                    color.bindColor();
                    front.renderGUI();
                    if(renderFps) text.renderString(fps + "", -1f, 1f, 0.5f, new Color(0.57f, 2.37f, 0.4f));
                    GL11.glFlush();
                    GLFW.glfwSwapBuffers(winManager.window);
            
                    fpsBuf++;
                    totalFramesRendered++;
                }
                
                GLFW.glfwPollEvents();
            }
        }
        
        /**
         * Changes the 3D Field-of-View.
         * @param fov The 3D Field-of-View angle.
         */
        public void setFOV(float fov)
        {
            this.fov = fov;
            gameConfig.setProperty("FOV", fov);
            winManager.closeWindow();
            winManager.openWindow();
        }
        
        /**
         * Changes the max FPS the game is allowed to render at.
         * When 0, there is no limit.
         * @param max The maximum number of frames that should be rendered in a second.
         */
        public void setMaxFPS(int max)
        {
            getConfig().setProperty("MaxFps", max);
            maxFps = max;
        }
        
        /**
         * Changes the state of whether or not to render the FPS.
         * @param render If true, the FPS will be rendered in text at the top-left of the screen.
         */
        public void setRenderFPS(boolean render)
        {
            this.renderFps = render;
        }
        
        /**
         * Sets the standard {@link wrath.client.graphics.Color} that will be used to render unless specified otherwise by OpenGL code.
         * @param color The standard {@link wrath.client.graphics.Color} to set.
         */
        public void setStandardColor(Color color)
        {
            this.color = color;
        }
        
        /**
         * Sets the game's global text renderer.
         * @param text The {@link wrath.client.graphics.TextRenderer} to manage text rendering.
         */
        public void setTextRenderer(TextRenderer text)
        {
            this.text = text;
        }
    }
    
    private class RootGameEventHandler implements GameEventHandler
    {
        @Override
        public void onGameClose()
        {
            evManager.gameHandlers.stream().forEach((handler) -> 
            {
                handler.onGameClose();
            });
        }

        @Override
        public void onGameOpen()
        {
            evManager.gameHandlers.stream().forEach((handler) -> 
            {
                handler.onGameOpen();
            });
        }

        @Override
        public void onLoadJavaPlugin(Object loadedObject)
        {
            evManager.gameHandlers.stream().forEach((handler) ->
            {
                handler.onLoadJavaPlugin(loadedObject);
            });
        }
        
        @Override
        public void onTick()
        {
            evManager.gameHandlers.stream().forEach((handler) -> 
            {
                handler.onTick();
            });
        }

        @Override
        public void onWindowOpen()
        {
            evManager.gameHandlers.stream().forEach((handler) -> 
            {
                handler.onWindowOpen();
            });
        }

        @Override
        public void onResolutionChange(int oldWidth, int oldHeight, int newWidth, int newHeight)
        {
            evManager.gameHandlers.stream().forEach((handler) -> 
            {
                handler.onResolutionChange(oldWidth, oldHeight, newWidth, newHeight);
            });
        }
        
    }
    
    private class RootInputEventHandler implements InputEventHandler
    {
        @Override
        public void onCharInput(char c)
        {
            evManager.inpHandlers.stream().forEach((handler) -> 
            {
                handler.onCharInput(c);
            });
        }

        @Override
        public void onCursorMove(double x, double y)
        {
            evManager.inpHandlers.stream().forEach((handler) -> 
            {
                handler.onCursorMove(x, y);
            });
        }

        @Override
        public void onScroll(double xoffset, double yoffset)
        {
            evManager.inpHandlers.stream().forEach((handler) -> 
            {
                handler.onScroll(xoffset, yoffset);
            });
        }
        
    }
    
    /**
     * Class to manage every 'Closeable' interface in the game.
     */
    private class TrashCollector
    {
        protected final ArrayList<Closeable> list = new ArrayList<>();
        
        private TrashCollector(){}
        
        private void run()
        {
            ArrayList<Closeable> cpy = new ArrayList<>();
            cpy.addAll(list);
            cpy.stream().forEach((c) -> 
            {
                c.close();
            });
        }
    }
    
    /**
     * Class to manage anything to do with the Game Window.
     */
    public class WindowManager
    {
        private boolean firstOpen = true;
        
        private long window;
        private boolean windowOpen = false;
        private WindowState windowState = null;

        private int width = 800;
        private int height = 600;

        /**
         * Constructor.
         * Protected so multiple instances aren't made pointlessly.
         */
        private WindowManager(){}
        
        /**
        * Centers the window in the middle of the designated primary monitor.
        * Does not work in Fullscreen or Fulscreen_Windowed modes.
        */
        public void centerWindow()
        {
            if(windowState == WindowState.FULLSCREEN || windowState == WindowState.FULLSCREEN_WINDOWED) return;
            
            GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
            GLFW.glfwSetWindowPos(window, (vidmode.width() / 2) - (width / 2), (vidmode.height() / 2) - (height / 2));
        }
    
        /**
        * Destroys and deallocates all GLFW/window resources.
        */
        public void closeWindow()
        {
            if(!windowOpen) return;
            windowOpen = false;
        
            gameLogger.println("Closing window [" + width + "x" + height + "]");
        
            winSizeStr.release();
            trashCollector.run();
            AL.destroy(audiocontext);
            GLFW.glfwDestroyWindow(window);
            
            gameConfig.save();
        }
        
        /**
         * Returns the height of the window.
         * @return Returns the height (in pixels) of the window.
         */
        public int getHeight()
        {
            return height;
        }
        
        /**
         * Gets the GLFW Window ID.
         * @return Returns the {@link org.lwjgl.glfw.GLFW} window ID.
         */
        public long getWindowID()
        {
            return window;
        }

        /**
         * Returns the width of the window.
         * @return Returns the width (in pixels) of the window.
         */
        public int getWidth()
        {
            return width;
        }

        /**
         * Gets the current state of the window as of
         * {@link wrath.client.enums.WindowState}.
         * @return Returns the current state of the window.
         */
        public WindowState getWindowState()
        {
            return windowState;
        }
        
        /**
         * Tells whether or not the window is open.
         * @return Returns true if the window is open, otherwise false.
         */
        public boolean isWindowOpen()
        {
            return windowOpen;
        }

        /**
         * Force minimizes the window.
         */
        public void minimizeWindow()
        {
            if(!windowOpen) return;
            GLFW.glfwIconifyWindow(window);
        }
        
        /**
         * Method to start the display. Made independent from start() so window
         * options can be adjusted without restarting game.
         */
        public void openWindow()
        {
            if(windowOpen) return;
            
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_DOUBLE_BUFFER, ClientUtils.getOpenGLBoolean(gameConfig.getBoolean("DoubleBuffered", true)));
            GLFW.glfwWindowHint(GLFW.GLFW_STEREO, ClientUtils.getOpenGLBoolean(gameConfig.getBoolean("RenderStereostopic", false)));
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, ClientUtils.getOpenGLBoolean(gameConfig.getBoolean("WindowResizable", true)));
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, ClientUtils.getOpenGLBoolean(gameConfig.getBoolean("APIForwardCompatMode", false)));
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, ClientUtils.getOpenGLBoolean(gameConfig.getBoolean("DebugMode", false)));
            GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, gameConfig.getInt("AntiAliasingSamples", 8));
            GLFW.glfwWindowHint(GLFW.GLFW_REFRESH_RATE, gameConfig.getInt("DisplayRefreshRate", 0));

            windowState = WindowState.valueOf(gameConfig.getString("WindowState", "fullscreen_windowed").toUpperCase());
            
            if(windowState == WindowState.FULLSCREEN) 
            {
                GLFWVidMode videomode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
                if(!gameConfig.getBoolean("FullscreenUsesResolution", false))
                {
                    gameConfig.setProperty("Width", videomode.width());
                    gameConfig.setProperty("Height", videomode.height());
                    width = videomode.width();
                    height = videomode.height();
                }

                window = GLFW.glfwCreateWindow(videomode.width(), videomode.height(), TITLE, GLFW.glfwGetPrimaryMonitor(), MemoryUtil.NULL);
            }
            else if(windowState == WindowState.FULLSCREEN_WINDOWED) 
            {
                GLFWVidMode videomode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
                width = videomode.width();
                height = videomode.height();

                GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GL11.GL_FALSE);
                window = GLFW.glfwCreateWindow(width, height, TITLE, MemoryUtil.NULL, MemoryUtil.NULL);
            }
            else if(windowState == WindowState.WINDOWED) 
            {
                width = gameConfig.getInt("Width", 800);
                height = gameConfig.getInt("Height", 600);
                window = GLFW.glfwCreateWindow(width, height, TITLE, MemoryUtil.NULL, MemoryUtil.NULL);
            }
            else if(windowState == WindowState.WINDOWED_UNDECORATED) 
            {
                GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GL11.GL_FALSE);

                width = gameConfig.getInt("Width", 800);
                height = gameConfig.getInt("Height", 600);
                window = GLFW.glfwCreateWindow(width, height, TITLE, MemoryUtil.NULL, MemoryUtil.NULL);
            }

            if(window == MemoryUtil.NULL) 
            {
                System.err.println("Could not initialize window! Window Info[" + width + "x" + height + "]");
                ClientUtils.throwInternalError("Window failed to initialize!", false);
                stopImpl();
            }

            gameLogger.println("Opened window [" + width + "x" + height + "] in " + windowState.toString().toUpperCase() + " mode.");

            inpManager.openInput();
            
            GLFW.glfwMakeContextCurrent(window);
            if(gameConfig.getBoolean("DisplayVsync", false)) GLFW.glfwSwapInterval(1);
            else GLFW.glfwSwapInterval(0);
            GL.createCapabilities();
            
            audiocontext = ALContext.create();
            audiocontext.makeCurrent();

            GLFW.glfwSetFramebufferSizeCallback(window, (winSizeStr = new GLFWFramebufferSizeCallback()
            {
                @Override
                public void invoke(long window, int width, int height)
                {
                    if(width <= 0 || height <= 0) return;

                    int ow = winManager.width;
                    int oh = winManager.height;
                    winManager.width = width;
                    winManager.height = height;

                    gameConfig.setProperty("Width", width);
                    gameConfig.setProperty("Height", height);
                    GL11.glViewport(0, 0, width, height);
                    evManager.getGameEventHandler().onResolutionChange(ow, oh, width, height);
                }
            }));

            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glViewport(0, 0, width, height);
            if(gameConfig.getBoolean("FullscreenUsesResolution", false) && gameConfig.getString("WindowState").equalsIgnoreCase("Fullscreen"))
            {
                width = gameConfig.getInt("Width", 800);
                height = gameConfig.getInt("Height", 600);
                winManager.setResolution(width, height);
            }
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            if(gameConfig.getInt("AntiAliasingSamples", 8) > 0) GL11.glEnable(GL13.GL_MULTISAMPLE);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            
            if(renManager.text == null) renManager.text = new TextRenderer(new File("assets/fonts/arial.png"), 0.75f);
            
            if(MODE == RenderMode.Mode3D)
            {
                renManager.projMatrix = ClientUtils.createProjectionMatrix(renManager.fov);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LESS);
            }
            ShaderProgram.DEFAULT_SHADER = ShaderProgram.loadShaderProgram(new File("assets/shaders/defaultshader.vert"), new File("assets/shaders/defaultshader.frag"));
            ShaderProgram.DEFAULT_TERRAIN_SHADER = ShaderProgram.loadShaderProgram(new File("assets/shaders/defaultterrainshader.vert"), new File("assets/shaders/defaultterrainshader.frag"));
            
            if(firstOpen) firstOpen = false;
            else refresher.run();
            
            windowOpen = true;
            evManager.getGameEventHandler().onWindowOpen();
            GLFW.glfwShowWindow(window);
        }

        /**
         * Takes a screen-shot and saves it to the file specified as a PNG.
         * @param saveToName The name of the file to save the screen-shot to (excluding file extension).
         */
        public void screenShot(String saveToName)
        {
            screenShot(saveToName, ImageFormat.PNG);
        }

        /**
         * Takes a screen-shot and saves it to the file specified.
         * @param saveToName The name of the file to save the screen-shot to (excluding file extension).
         * @param format The format to save the image as.
         */
        public void screenShot(String saveToName, ImageFormat format)
        {
            GL11.glReadBuffer(GL11.GL_FRONT);
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            
            
            Thread t = new Thread(() -> 
            {
                File screenshotDir = new File("etc/screenshots");
                if(!screenshotDir.exists()) screenshotDir.mkdirs();
                File saveTo = new File("etc/screenshots/" + saveToName + "." + format.name().toLowerCase());
                BufferedImage image = ClientUtils.getByteBufferToImage(buffer, width, height);
                try 
                {
                    ImageIO.write(image, format.name(), saveTo);
                    gameLogger.println("Saved screenshot '" + saveTo.getName() + "'!");
                }
                catch(IOException e) 
                {
                    System.err.println("Could not save Screenshot to '" + saveTo.getName() + "'! I/O Error has occured!");
                }
            });

            t.start();
        }
        
        /**
        * Changes the size of the window.
        * @param width New width of the window, measured in pixels.
        * @param height New height of the window, measures in pixels.
        */
        public void setResolution(int width, int height)
        {
            this.width = width;
            this.height = height;
            GLFW.glfwSetWindowSize(window, width, height);
            GL11.glViewport(0, 0, width, height);
        
            gameConfig.setProperty("Width", width);
            gameConfig.setProperty("Height", height);
        }
    
        /**
        * Changes the state of the window.
        * This method will require the window to restart, and this will be done via {@link wrath.client.Game.WindowManager#closeWindow() } and {@link wrath.client.Game.WindowManager#openWindow() }.
        * @param state The state to set the window to.
        */
        public void setWindowState(WindowState state)
        {
            gameConfig.setProperty("WindowState", state.toString().toUpperCase());
            if(windowOpen)
            {
                closeWindow();
                openWindow();
            }
        }
    }
}