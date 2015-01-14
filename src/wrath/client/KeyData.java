/**
 *  Wrath Engine
 *  Copyright (C) 2015 Trent Spears
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

import org.lwjgl.glfw.GLFW;

/**
 * Keeps track of keys.
 * @author Trent Spears
 */
public class KeyData
{
    /**
     * Used to differentiate between whether the action should execute when a key is pressed or released.
     */
    public static enum KeyAction {KEY_HOLD_DOWN, KEY_PRESS, KEY_RELEASE;}
    
    private final KeyAction action;
    private final int actionRaw;
    private final Runnable event;
    private final int key;
    
    public KeyData(KeyAction action, Runnable event, int glfwKey)
    {
        this.action = action;
            
        if(action == KeyAction.KEY_RELEASE) this.actionRaw = GLFW.GLFW_RELEASE;
            else this.actionRaw = GLFW.GLFW_PRESS;
        
        this.event = event;
        this.key = glfwKey;      
    }
    
    /**
     * Executes the event assigned to the key.
     */
    public void execute()
    {
        event.run();
    }
    
    /**
    * Gets the {@link wrath.client.Game.KeyAction} of the key set.
    * This is used to determine whether to execute the event when a key is pressed, or execute the event when the key is released.
    * @return Returns the {@link wrath.client.Game.KeyAction} specified in the Constructor.
    */
    public KeyAction getAction()
    {
        return action;
    }
        
    /**
     * Gets the {@link wrath.client.Key}.
     * @return Returns the key id.
     */
    public int getKey() 
    {
        return key;
    }

    /**
     * Used by the internal engine to obtain the GLFW action code.
     * @return Returns the int version of the KeyAction for use with GLFW.
     */
    public int getRawAction() 
    {
        return actionRaw;
    }   
}