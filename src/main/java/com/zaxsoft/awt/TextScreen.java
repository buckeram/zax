/**
 * Copyright (c) 2008 Matthew E. Kimmel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.zaxsoft.awt;

import java.awt.*;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.util.StringTokenizer;
import java.util.Vector;

/** The TextScreen class is an extension of the Java AWT Component class which
    provides the functionality of a text screen (similar to DOS in text mode),
    which can print text and optionally scroll up or down.  Methods are also
    provided to read and optionally echo keyboard input.  In addition, "regions"
    may be defined such that the active region is the only area of the screen
    affected by some operations such as clearing and scrolling.  This component
    is designed for use with fixed-width fonts.  I make no guarantees about
    its functionality when used with a proportional font.
    
    A TextScreen component may have various attributes associated with it.
    These attributes may be set with the constructor or with the setAttributes
    method, and may beretrieved with the getAttributes method.  Attributes are
    int constants which or OR'd together to form an attribute word.  Attributes
    include:
        TextScreen.KEYECHO - Echo keyboard input during reads
        TextScreen.CURSOR - Display a text cursor
        TextScreen.AUTOWRAP - Wrap at end of line.  For best results, don't use this.
        
    @see java.awt.Canvas
    @author Matt Kimmel
*/

public class TextScreen extends java.awt.Component
{
    // Public constants
    // Attributes
    public static final int KEYECHO = 0x01; // Echo keyboard input
    public static final int CURSOR = 0x02; // Display a cursor
    public static final int AUTOWRAP = 0x04; // Autowrap lines
    
    // Constants
    // States -- These are protected so they can be seen by inner classes
    protected static final int WRITE = 1; // Not reading
    protected static final int READLINE = 2; // In readLine mode
    protected static final int READCHAR = 3; // In readChar mode
    
    // Private variables
    private int rows, cols; // Current rows and columns
    private int attributes; // Current attributes
    private boolean rvsMode; // In reverse video mode?
    private int state; // Current input state
    private Thread readThread; // The thread currently waiting for input
    private StringBuffer readString; // String currently being read
    private int readTerminator; // Key that terminated most recent READLINE
    private int curReadChar; // Character from most recent READCHAR
    private final FontMetrics curFontMetrics; // Current font metrics
    private final int fontHeight;
    private final int fontMaxWidth;
    private final int fontAscent; // Size of current font
    private Rectangle curRegion; // Rectangle describing active region
    private Vector terminators; // Characters that terminate a line of input
    private Image offscreen; // Image used for offscreen drawing
    private int curRow, curCol; // Row and column of text cursor
    private int cursorX, cursorY; // Location of visible cursor
    
    
    // Inner classes
    
    // TextScreenKeyboardEventHandler handles keyboard events for the TextScreen.
    class TextScreenKeyboardEventHandler extends java.awt.event.KeyAdapter
    {
        TextScreen parentObject; // Object to notify at the appropriate times
        
        public TextScreenKeyboardEventHandler(TextScreen myParent)
        {
            this.parentObject = myParent;
        }
        
        @Override
        public void keyTyped(java.awt.event.KeyEvent e)
        {
            int key = (int)e.getKeyChar();
            
            switch (this.parentObject.state)
            {
                case TextScreen.READLINE : // Reading a line
                    if (this.parentObject.terminators.contains(new Integer(key)))
                    {
                        this.parentObject.state = WRITE;
                        this.parentObject.readTerminator = key;
                        if (((char)key == '\r' || (char)key == '\n') && (this.parentObject.attributes & KEYECHO) == KEYECHO)
                            this.parentObject.printString("\n");
                        this.parentObject.readThread.interrupt();
                        return;
                    }
                    if ((this.parentObject.attributes & KEYECHO) == KEYECHO && !((char)key == '\b' && this.parentObject.readString.length() == 0))
                        this.parentObject.printString(String.valueOf((char)key));
                    switch ((char)key)
                    {
                        case '\b' : 
                            if (this.parentObject.readString.length() > 0)
                                this.parentObject.readString.setLength(this.parentObject.readString.length() - 1);
                            break;
                        default :
                            this.parentObject.readString.append(String.valueOf((char)key));
                            break;
                    }
                    return;

                case READCHAR :
                    this.parentObject.curReadChar = key;
                    if ((this.parentObject.attributes & KEYECHO) == KEYECHO)
                        this.parentObject.printString(String.valueOf((char)key));
                    this.parentObject.state = WRITE;
                    this.parentObject.readThread.interrupt();
                    return;
                    
                case WRITE :
                    return; // extra keystroke
            }
        }
    }
    
    // Methods
    
    // Constructor
    
    /** Creates a TextScreen with the specified colors, fonts, attributes, rows,
        and columns.
        
        @param initialFont The initial font to be used
        @param bgColor The background color
        @param fgColor The foreground color
        @param initialRows The initial number of rows of text
        @param initialCols The initial numner of columns of text
        @param attrs The initial attributes
    */
    public TextScreen(Font initialFont,Color bgColor,Color fgColor,
                        int initialRows,int initialCols,int attrs)
    {

        // Initialize variables
        this.rows = initialRows;
        this.cols = initialCols;
        this.attributes = attrs;
        this.rvsMode = false;
        this.state = WRITE;
        setBackground(bgColor);
        setForeground(fgColor);
        setFont(initialFont);
        
        // Set up fonts and related variables
        this.curFontMetrics = getFontMetrics(getFont());
        this.fontHeight = this.curFontMetrics.getHeight();
        if (getFont().getName().equals("Monospaced"))
            this.fontMaxWidth = this.curFontMetrics.charWidth('W'); // getMaxAdvance is sometimes misleading for fixed-width fonts
        else
            this.fontMaxWidth = this.curFontMetrics.getMaxAdvance();
//        fontMaxWidth = 0;
//        int widths[] = curFontMetrics.getWidths();
//        for (int i = 0; i < 128; i++)
//            if (widths[i] > fontMaxWidth)
//                fontMaxWidth = widths[i];
        this.fontAscent = this.curFontMetrics.getMaxAscent();
        
        // Set the default region, which is the entire screen
        this.curRegion = new Rectangle(0,0, this.cols, this.rows);
        
        // Initialize terminating characters.  CR is one by default.
        this.terminators = new Vector();
        this.terminators.addElement(new Integer(13));
        
        // Add key listener
        addKeyListener(new TextScreenKeyboardEventHandler(this));
    }
    
    // Public methods
    
    /** Sets the terminators (characters which terminate a readLine call) to
        the characters in the given buffer.
        
        @param chars New vector of terminating characters
    */
    public void setTerminators(Vector chars)
    {
        this.terminators = (Vector)chars.clone();
    }
    
    /** Returns a vector of the current terminators.
    
        @return Vector containing current terminators.
    */
    public Vector getTerminators()
    {
        return this.terminators;
    }
    
    /** Resizes the TextScreen to fit the current font, rows and columns.
        Also resets the region to the entire screen, and gets current font
        metrics.
    */
    public void resizeToFit()
    {
        int width, height;
        
        // Compute the size and resize the component
        height = this.fontHeight * this.rows;
        width = this.fontMaxWidth * this.cols;
        setSize(width,height);
        this.curRegion = new Rectangle(0,0, this.cols, this.rows);
        
        // Get a new offscreen drawing area
        Dimension s = getSize();
        this.offscreen = createImage(s.width,s.height);
        clearScreen();
    }

    /** Clears the region and homes the cursor.
    */
    public void clearScreen()
    {
        Graphics g = this.offscreen.getGraphics();
        g.setColor(getBackground());
        g.fillRect(
            this.curRegion.x* this.fontMaxWidth,
            this.curRegion.y* this.fontHeight,
            this.curRegion.width* this.fontMaxWidth,
            this.curRegion.height* this.fontHeight);
        this.curRow = this.curRegion.x;
        this.curCol = this.curRegion.y;
        this.cursorX = this.curCol * this.fontMaxWidth;
        this.cursorY = this.curRow * this.fontHeight;
        g = getGraphics();
        paint(g);
    }
    
    /** Set the region.
    
        @param region New region
    */
    public void setRegion(Rectangle region)
    {
        this.curRegion = region;
    }
    
    /** Get the current region.
    
        @return Current region
    */
    public Rectangle getRegion()
    {
        return this.curRegion;
    }
    
    /** Print a string, wrapping as necessary.  Affected by region.
    
        @param str String to print
    */
    public void printString(String str)
    {
        Dimension s = getSize();
        Graphics g = this.offscreen.getGraphics();
        int maxRow, maxCol;
        
        // Determine last row and last column (absolute) based on region
        maxCol = this.curRegion.x + this.curRegion.width;
        maxRow = this.curRegion.y + this.curRegion.height;
        
        // Set color and font in graphics context
        g.setColor(getForeground());
        g.setFont(getFont());
        
        // Tokenize the string into lines, backspaces and newlines, so we can
        // output entire strings at a time, for drawing efficiency.
        StringTokenizer st = new StringTokenizer(str,"\b\n\r",true);
        
        // Now process one token at a time.
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            
            // See if this is a newline--if so, process it
            if (token.equals("\n"))
            {
                // Cursor X position becomes start of new line
                this.cursorX = this.curRegion.x * this.fontMaxWidth;
                this.curCol = this.curRegion.x;
                
                // Scroll if necessary
                if (this.curRow + 1 == maxRow)
                    scrollUp(1); // As a side effect, curRow is decremented and cursorY is set up a line
                
                // Update Y position of cursor, then go to next token
                this.curRow++;
                this.cursorY = this.cursorY + this.fontHeight;
                continue;
            }
            
            // If this is a backspace, process it.
            if (token.equals("\b"))
            {
                // If we are not at the start of a line, just go back one character.
                // If we are, go to the end of the previous line.  If we are at
                // 0,0 in the current region, just erase the current character.
                // Is this last behavior correct?  Does it work with ZMachine?
                if (this.curCol > this.curRegion.x)
                    gotoXY(this.curCol -1, this.curRow);
                else if (this.curRow > this.curRegion.y)
                    gotoXY(this.cols -1, this.curRow -1);
                
                // Clear the current character
                g.setColor(getBackground());
                g.fillRect(this.cursorX, this.cursorY, this.fontMaxWidth, this.fontHeight);
                g.setColor(getForeground());
                
                // Go to next token
                continue;
            }
            
            // If this is a CR (not a newline), go to the beginning of the current line.
            if (token.equals("\r"))
            {
                gotoXY(this.curRegion.x, this.curRow);
                continue;
            }
            
            // We are going to print this token.  If AUTOWRAP is on, do crude
            // wrapping, setting token to the substring that will fit on the
            // last line.  After wrapping, token will be printed by default.
            // Wrapping is based on characters, not pixels, so this will look
            // pretty strange in a non-proportional font.
            if((this.attributes & AUTOWRAP) == AUTOWRAP)
            {
                while (this.curCol + token.length() >= maxCol)
                {
                    // Draw what we can, do a newline, reduce the string.
                    String sub = token.substring(0, maxCol - this.curCol);
                    
                    if (this.rvsMode)
                    {
                        g.fillRect(
                            this.cursorX, this.cursorY, this.curFontMetrics.stringWidth(sub),
                            this.fontHeight);
                        g.setColor(getBackground());
                    }
                    else
                    {
                        g.setColor(getBackground());
                        g.fillRect(
                            this.cursorX, this.cursorY, this.curFontMetrics.stringWidth(sub),
                            this.fontHeight);
                        g.setColor(getForeground());
                    }
                    
                    g.drawString(sub, this.cursorX, this.cursorY + this.fontAscent);

                    if (this.rvsMode)
                        g.setColor(getForeground());
                    
                    token = token.substring(maxCol - this.curCol);
                    
                    // Scroll, as above
                    this.cursorX = this.curRegion.x * this.fontMaxWidth;
                    this.curCol = this.curRegion.x;
                    if (this.curRow + 1 == maxRow)
                        scrollUp(1);
                    this.curRow++;
                    this.cursorY = this.cursorY + this.fontHeight;
                }
            }
            
            // Draw the token.  If we have done wrapping, this is the portion of
            // the token that will fit on the last line.  Otherwise, it is the entire
            // token.
            if (this.rvsMode)
            {
                g.fillRect(this.cursorX, this.cursorY, this.curFontMetrics.stringWidth(token), this.fontHeight);
                g.setColor(getBackground());
            }
            else
            {
                g.setColor(getBackground());
                g.fillRect(this.cursorX, this.cursorY, this.curFontMetrics.stringWidth(token), this.fontHeight);
                g.setColor(getForeground());
            }
            
            g.drawString(token, this.cursorX, this.cursorY + this.fontAscent);
            
            if (this.rvsMode)
                g.setColor(getForeground());

            this.curCol += token.length();
            this.cursorX += token.length() * this.fontMaxWidth; // We do not use stringWidth here, for the sake of consistency.
        }
        
        // Repaint the screen with the changes
        g = getGraphics();
        paint(g);
    }
    
    /** Do a newline.  Equivalent to printString("\n"), but slightly more efficient.
        Affected by region.
    */
    public void newline()
    {
        // Adjust cursor position
        this.cursorX = this.curRegion.x * this.fontMaxWidth;
        this.curCol = this.curRegion.x;
        this.cursorY = this.cursorY + this.fontHeight;
        this.curRow++;
        
        // Scroll if necessary.  As side effects, curRow and cursorY are
        // updated when we scroll.  Therefore,
        // if no scrolling happens, we need to repaint the screen.
        if (this.curRow == this.curRegion.x + this.curRegion.height)
            scrollUp(1);

        Graphics g = getGraphics();
        paint(g);
    }
    
    /** Move the cursor to the specified x/y coordinates (specified in characters).
        If either coordinate is out of range, the cursor will move to the edge
        of the screen in that direction.  The coordinates are absolute and are
        NOT affected by region.
        
        @param x The x coordinate to go to.
        @param y The y coordinate to go to.
    */
    public void gotoXY(int x,int y)
    {
        if (x < this.cols)
            this.curCol = x;
        else
            this.curCol = this.cols - 1;
        
        if (y < this.rows)
            this.curRow = y;
        else
            this.curRow = this.rows - 1;

        this.cursorX = this.curCol * this.fontMaxWidth;
        this.cursorY = this.curRow * this.fontHeight;
    }
    
    /** Scroll the region up by the specified number of lines.  If the number
        of lines is greater than the number of lines in the region, the region
        will be cleared.
        
        @param numLines The number of lines to scroll
    */
    public void scrollUp(int numLines)
    {
        // Validate parameter
        if (numLines < 1)
            return;
            
        // Just clear the region if the number of lines is out of range
        if (numLines >= this.curRegion.height)
        {
            clearScreen();
            return;
        }
        
        // Copy a region of the drawing area upwards; fill in the remaining area
        // with the background color.
        Graphics g = this.offscreen.getGraphics();
        Dimension s = getSize();
        int scrollSize = numLines * this.fontHeight;
        g.copyArea(
            this.curRegion.x * this.fontMaxWidth,
                    this.curRegion.y * this.fontHeight + scrollSize,
            this.curRegion.width * this.fontMaxWidth,
                    this.curRegion.height * this.fontHeight - scrollSize,
                    0,-scrollSize);
        g.setColor(getBackground());
        g.fillRect(
            this.curRegion.x * this.fontMaxWidth,
            (this.curRegion.y + this.curRegion.height) * this.fontHeight - scrollSize,
            this.curRegion.width * this.fontMaxWidth,
                    scrollSize);
        g.setColor(getForeground());
        
        // Adjust cursor coordinates
        this.curRow = this.curRow - numLines;
        this.cursorY = this.curRow * this.fontHeight;
    }
    
    /** Set or clear reverse video mode.
    
        @param mode true for reverse video on, false for reverse video off
    */
    public void reverseVideo(boolean mode)
    {
        this.rvsMode = mode;
    }
    
    /** Add the specified text style to the styles for this font, unless it's
        Font.PLAIN, in which case everything goes back to normal.
        
        @param style Font style to add
    */
    public void setFontStyle(int style)
    {
        Font f = getFont();
        int newstyle;
        
        if (style == Font.PLAIN)
            newstyle = Font.PLAIN;
        else
            newstyle = f.getStyle() | style;
        
        setFont(new Font(f.getName(),newstyle,f.getSize()));
    }
    
    /** Return the current position (in characters) of the cursor.

        @return Point describing the position of the cursor.
    */
    public Point getCursorPosition()
    {
        Point d = new Point(this.curCol, this.curRow);
        return d;
    }
    
    /** Return the current size (in characters) of the screen.
    
        @return Dimension describing the size of the screen.
    */
    public Dimension getScreenSize()
    {
        Dimension d = new Dimension(this.cols, this.rows);
        return d;
    }
    
    /** Set the current size (in characters) of the screen.  Clears the screen.
    
        @param width New width of screen
        @param height New height of screen
    */
    public synchronized void setScreenSize(int width,int height)
    {
        this.rows = height;
        this.cols = width;
        resizeToFit();
    }
    
    /** Set the current size (in characters) of the screen.  Don't clear the screen;
        if saveBottom is true, save the bottom portion of the screen if the screen is
        shrinking; otherwise, save the top portion.
        
        @param width New width of screen
        @param height New height of screen
        @param saveBottom If true, save the bottom of the screen; if false, save the top.
    */
    public synchronized void setScreenSize(int width,int height,boolean saveBottom)
    {
        Image oldScreen;
        Rectangle r = new Rectangle();
        int oldRows, oldCols;
        
        // First, save any portion of the screen that we need to.
        if (height < this.rows)
        {
            if (saveBottom)
            {
                r.x = 0;
                r.y = this.rows * this.fontHeight - height * this.fontHeight;
                r.width = width * this.fontMaxWidth;
                r.height = height * this.fontHeight;
            }
            else
            {
                r.x = 0;
                r.y = 0;
                r.width = width * this.fontMaxWidth;
                r.height = height * this.fontHeight;
            }
        }
        else // We're making the screen bigger, so copy the whole thing
        {
            r.x = 0;
            r.y = 0;
            r.width = getSize().width;
            r.height = getSize().height;
        }
        
        // Copy the screen portion we just set up.
        CropImageFilter filter = new CropImageFilter(r.x,r.y,r.width,r.height);
        oldScreen = getToolkit().createImage(new FilteredImageSource(this.offscreen.getSource(),filter));
        
        // Set up new size
        oldRows = this.rows;
        oldCols = this.cols;
        this.rows = height;
        this.cols = width;
        
        // Compute the size and resize the component
        height = this.fontHeight * this.rows;
        width = this.fontMaxWidth * this.cols;
        setSize(width,height);
        
        // Get a new offscreen drawing area
        Dimension s = getSize();
        this.offscreen = createImage(s.width,s.height);
        
        // Clear it, then put the old image data back in it.
        Graphics g = this.offscreen.getGraphics();
        g.setColor(getBackground());
        g.fillRect(0,0,s.width,s.height);
        g.setColor(getForeground());
        g.drawImage(oldScreen,0,0,this);
        
        // Put the cursor in an appropriate place.  Adjust it if necessary to keep it
        // in the same place in the text; if it is offscreen, home it.
        this.curRow -= oldRows - this.rows;
        if (this.curRow < 0 || this.curRow >= this.rows || this.curCol < 0 || this.curCol >= this.cols)
        {
            this.curCol = 0;
            this.curRow = 0;
        }
        this.cursorX = this.curCol * this.fontMaxWidth;
        this.cursorY = this.curRow * this.fontHeight;
        
        // Paint everything
        g = getGraphics();
        paint(g);
    }
    
    /** Return the size of the current font.
    
        @return Dimension describing the size of the largest character.
    */
    public Dimension getFontSize()
    {
        Dimension d = new Dimension(this.fontMaxWidth, this.fontHeight);
        return d;
    }
    
    /** Set TextScreen attributes.
    
        @param attrs Bitmask of TextScreen attributes
    */
    public void setAttributes(int attrs)
    {
        this.attributes = attrs;
    }
    
    /** Get the current TextScreen attributes
    
        @return Bitmask of current TextScreen attributes
    */
    public int getAttributes()
    {
        return this.attributes;
    }

    /** Read a line of text from the keyboard and append it to a supplied
        StringBuffer.  Return the character that terminated the operation.
        If KEYECHO is on, echo the characters typed.
        
        @param sb Stringbuffer to append text to.
        
        @return The key that caused the line to terminate, or -2 on error.
    */
    public synchronized int readLine(StringBuffer sb)
    {
        Graphics g = getGraphics();
        
        // Draw the cursor, in case it disappeared.
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getForeground());
            g.fillRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }
        
        // Set up readline state
        this.readString = sb;
        this.readThread = Thread.currentThread();
        this.state = READLINE;
        
        // Now wait until we are notified by the event handler that
        // the readline has terminated (or we're interrupted).
        try
        {
            while (true) // Why can't I sleep indefinitely?
                Thread.currentThread().sleep(Long.MAX_VALUE);
        }
        catch (InterruptedException ex)
        {
            // Don't actually do anything here
        }
        this.readThread = null;
        
        // We resumed without error, so the completed string is in readString.
        // Clear the cursor, since this is the expected state.
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getBackground());
            g.clearRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }
        
        // Return the key that terminated the read
        return this.readTerminator;
    }
    
    /** As readLine(StringBuffer), except that the read will time out after
        the given number of milliseconds.
        
        @param sb Stringbuffer to which to append read text
        @param time Number of milliseconds before timeout
        
        @return Key that terminated input, or -1 on timeout, or -2 on error
    */
    public synchronized int readLine(StringBuffer sb,long time)
    {
        Graphics g = getGraphics();
        boolean timedOut = false;
        
        // Draw the cursor, in case it disappeared.
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getForeground());
            g.fillRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }
    
        // Set up for read mode
        this.readString = sb;
        this.readThread = Thread.currentThread();
        this.state = READLINE;
        
        // Wait for signal from event handler, as above
        try
        {
            Thread.currentThread().sleep(time);
        }
        catch (InterruptedException ex)
        {
            // Do nothing
        }
        this.readThread = null;
        
        // If state is still READLINE, that means we did not terminate
        // (that is, we timed out)
        if (this.state == READLINE)
        {
            this.state = WRITE;
            timedOut = true;
        }
        
        // Nuke the cursor
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getBackground());
            g.clearRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }

        // Return an appropriate value
        if (timedOut)
            return -1;
        else
            return this.readTerminator;
    }
    
    /** Read a character from the keyboard and return it.  If KEYECHO is on,
        echo it.
        
        @return Key that was pressed.  0 on error.
    */
    public synchronized int readChar()
    {
        Graphics g = getGraphics();
        
        // Draw the cursor, in case it disappeared.
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getForeground());
            g.fillRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }
        
        // Set up for readchar mode
        this.readThread = Thread.currentThread();
        this.state = READCHAR;
        
        // Wait for event handler to notify us
        try
        {
            while (true)
                Thread.currentThread().sleep(Long.MAX_VALUE);
        }
        catch (InterruptedException ex)
        {
            // Do nothing
        }
        this.readThread = null;
        
        // Erase the cursor
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getBackground());
            g.clearRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }
            
        // The character is in curReadChar
        return this.curReadChar;
    }
    
    /** Read a character from the keyboard and return it, timing out after
        the specified number of milliseconds.
        
        @param time Number of milliseconds before timeout
        
        @return Key pressed, or -1 on timeout, or -2 on error.
    */
    public synchronized int readChar(long time)
    {
        Graphics g = getGraphics();
        boolean timedOut = false;
        
        // Draw the cursor, in case it disappeared.
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getForeground());
            g.fillRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }

        // Set up for readchar mode
        this.readThread = Thread.currentThread();
        this.state = READCHAR;
        
        // Wait for notification or timeout
        try
        {
            Thread.currentThread().sleep(time);
        }
        catch (InterruptedException ex)
        {
            // Do nothing
        }
        this.readThread = null;
        
        // If state is still READCHAR, we timed out.
        if (this.state == READCHAR)
        {
            this.state = WRITE;
            timedOut = true;
        }
        
        // Get rid of the cursor
        if ((this.attributes & CURSOR) == CURSOR)
        {
            g.setColor(getBackground());
            g.clearRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }
        
        // Return the appropriate value
        if (timedOut)
            return -1;
        else
            return this.curReadChar;
    }
    
    /** Override of paint() method */
    @Override
    public void paint(Graphics g)
    {
        // Copy the offscreen drawing area to the screen.
        if (this.offscreen != null)
            g.drawImage(this.offscreen,0,0,this);
        
        // Draw the cursor if necessary
        if((this.attributes & CURSOR) == CURSOR && this.state != WRITE)
        {
            g.setColor(getForeground());
            g.fillRect(this.cursorX +1, this.cursorY +1, this.fontMaxWidth -2, this.fontHeight -2);
        }
    }
    
    /** Override of getMinimumSize() method **/
    @Override
    public Dimension getMinimumSize()
    {
        return new Dimension(this.cols * this.fontMaxWidth, this.rows * this.fontHeight);
    }
    
    /** Override of getMaximumSize() method **/
    @Override
    public Dimension getMaximumSize()
    {
        return new Dimension(this.cols * this.fontMaxWidth, this.rows * this.fontHeight);
    }
    
    /** Override of getPreferredSize() method **/
    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(this.cols * this.fontMaxWidth, this.rows * this.fontHeight);
    }
    
    /** Override of addNotify().  Does additional initialization that has
        to wait until a peer is created.
    */
    @Override
    public void addNotify()
    {
        super.addNotify();
        resizeToFit();
    }
}
