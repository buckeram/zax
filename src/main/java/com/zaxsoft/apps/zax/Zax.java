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
package com.zaxsoft.apps.zax;

import com.zaxsoft.awt.TextScreen;
import com.zaxsoft.zmachine.ZCPU;
import com.zaxsoft.zmachine.ZUserInterface;

import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Zax main class.
 *
 * @author Matt Kimmel
 */
class Zax extends Frame implements ZUserInterface {
	private static final String VERSION_STRING = "0.91";
    private TextScreen screen; // The main screen
    private ZaxWindow[] windows;  // Z-Machine Windows.
    private ZaxWindow curWindow; // The current window.
    private ZaxWindow statusBar; // The status bar, in V1-3
	private Dimension screenSize; // Size of the entire screen in characters
    private int version;    // Version of this storyfile - 0 if game not yet initialized.
    private int moreLines; // Number of lines before next MORE
    private Map<Integer, Integer> inputCharacters; // Used to translate between Event input characters and Z-Machine input characters
    private Vector<Integer> terminatingCharacters; // List of terminating characters for READ operations
	private Thread cpuThread; // Thread of ZMachine CPU
    
    // Main routine.  Just instantiates the class.
    public static void main(final String args[])
    {
        new Zax();
    }

    // Constructor
	public Zax()
	{
		// Set up the frame.
		setTitle("Zax v" + VERSION_STRING);

		setupMenuBar();
        
		// Put a screen up-this screen will be replaced later.
        setResizable(false);
        this.screen = new TextScreen(new Font("Monospaced",Font.PLAIN,12),
                                Color.blue,Color.white,25,80,0);
        add(this.screen);
        final Insets ins = getInsets();
        setSize(ins.left + ins.right + this.screen.getPreferredSize().width,
                ins.top + ins.bottom + this.screen.getPreferredSize().height);
        //show();
        setVisible(true);
	}

	private void setupMenuBar()
    {
        final MenuItem playMenuItem = new MenuItem("Play Story...");
        playMenuItem.addActionListener(e -> playStory());

        final MenuItem exitMenuItem = new MenuItem("Exit");
        exitMenuItem.addActionListener(e -> System.exit(0));

        final Menu menu = new Menu("File");
        menu.add(playMenuItem);
        menu.addSeparator();
        menu.add(exitMenuItem);

        final MenuBar menuBar = new MenuBar();
        menuBar.add(menu);
        setMenuBar(menuBar);
    }

	// Play a story file
    private void playStory()
    {
        // If a story is already running, ignore this (for now)
		if (this.cpuThread != null)
			return;

		// Create a ZMachine instance
        final ZCPU cpu = new ZCPU(this);

		// Allow the user to select a story file
		final FileDialog fd = new FileDialog(this,"Open a Storyfile", FileDialog.LOAD);
		fd.setVisible(true);
		final String dir = fd.getDirectory();
		final String file = fd.getFile();
		if (dir == null || file == null)
        {
            return;
        }
		final String pathname = dir + file;

        cpu.initialize(pathname);
        this.cpuThread = cpu.start();
	}

    // Private method called when switching windows
    private void switchWindow(final ZaxWindow w)
    {
        this.curWindow.cursorPosition = this.screen.getCursorPosition();
        this.curWindow = w;
        this.screen.setRegion(w.shape);
        setTextStyle(this.curWindow.tstyle);
        this.screen.gotoXY(w.cursorPosition.x,w.cursorPosition.y);
        if (w == this.windows[0])
        {
            this.screen.setAttributes(TextScreen.KEYECHO | TextScreen.CURSOR);
        }
        else
        {
            this.screen.setAttributes(0);
        }
    }
    
    
    /////////////////////////////////////////////////////////
    // ZUserInterface methods
    /////////////////////////////////////////////////////////

    // fatal - print a fatal error message and exit
    // Windows must be initialized!
    @Override
    public void fatal(final String message)
    {
        this.screen.printString("FATAL ERROR: " + message + "\n");
        this.screen.printString("Hit a key to exit.\n");
        this.screen.readChar();
        System.exit(1);
    }

    // Initialize the user interface.  This consists of setting
    // up a status bar and a lower window in V1-2, and an upper
    // and lower window in V3-5,7-8.  Not sure yet what this
    // involves in V6.
    @Override
    public void initialize(final int version)
    {
        // Initialize the Event-->Z-Character translation table.
        // Warning!  Hardcoded values ahead!
        this.inputCharacters = new HashMap<>(16);
        this.inputCharacters.put(new Integer(Event.UP),new Integer(129));
        this.inputCharacters.put(new Integer(Event.DOWN),new Integer(130));
        this.inputCharacters.put(new Integer(Event.LEFT),new Integer(131));
        this.inputCharacters.put(new Integer(Event.RIGHT),new Integer(132));
        this.inputCharacters.put(new Integer(Event.F1),new Integer(133));
        this.inputCharacters.put(new Integer(Event.F2),new Integer(134));
        this.inputCharacters.put(new Integer(Event.F3),new Integer(135));
        this.inputCharacters.put(new Integer(Event.F4),new Integer(136));
        this.inputCharacters.put(new Integer(Event.F5),new Integer(137));
        this.inputCharacters.put(new Integer(Event.F6),new Integer(138));
        this.inputCharacters.put(new Integer(Event.F7),new Integer(139));
        this.inputCharacters.put(new Integer(Event.F8),new Integer(140));
        this.inputCharacters.put(new Integer(Event.F9),new Integer(141));
        this.inputCharacters.put(new Integer(Event.F10),new Integer(142));
        this.inputCharacters.put(new Integer(Event.F11),new Integer(143));
        this.inputCharacters.put(new Integer(Event.F12),new Integer(144));
    
        // Set up the terminating characters.  Carriage Return
        // (13) is always a terminating character.  Also LF (10).
        this.terminatingCharacters = new Vector<>();
        this.terminatingCharacters.addElement(new Integer(13));
        this.terminatingCharacters.addElement(new Integer(10));
        
        // Set up the screen, etc
        this.version = version;
        if (this.screen != null)
        {
            remove(this.screen);
        }
        this.screenSize = new Dimension(80,25); // TODO: better way to set this?
        this.screen = new TextScreen(new Font("Monospaced", Font.PLAIN, 12),
            Color.blue, Color.white, this.screenSize.height,
            this.screenSize.width, 0);
        add(this.screen);
        final Insets ins = getInsets();
        setSize(ins.left + ins.right + this.screen.getPreferredSize().width,
                ins.top + ins.bottom + this.screen.getPreferredSize().height);
		setVisible(true);
        this.screen.setTerminators(this.terminatingCharacters);
        
        // Depending on which storyfile version this is, we set
        // up differently.
        if (this.version == 1 || this.version == 2) { // V1-2
            // For version 1-2, we set up a status bar and a
            // lower window.
            this.statusBar = new ZaxWindow(0,0, this.screenSize.width, 1);
            this.windows = new ZaxWindow[1];
            this.windows[0] = new ZaxWindow(0, 1, this.screenSize.width, this.screenSize.height-1);
            
            // Start off in window 0
            this.curWindow = this.windows[0];
            this.screen.gotoXY(this.windows[0].shape.x, this.windows[0].shape.y);
            this.screen.setAttributes(TextScreen.KEYECHO | TextScreen.CURSOR);
            this.screen.setRegion(this.windows[0].shape);
            this.windows[0].cursorPosition = this.screen.getCursorPosition();
            this.statusBar.cursorPosition = new Point(0, 0);
            return;
        }
        if (this.version == 3) { // V3
            // For V3, we set up a status bar AND two windows.
            // This all may change.
            this.statusBar = new ZaxWindow(0, 0, this.screenSize.width,1);
            this.windows = new ZaxWindow[2];
            this.windows[1] = new ZaxWindow(0, 1, this.screenSize.width,0);
            this.windows[0] = new ZaxWindow(0, 1, this.screenSize.width, this.screenSize.height-1);

            // Start off in window 0
            this.curWindow = this.windows[0];
            this.screen.setAttributes(TextScreen.KEYECHO | TextScreen.CURSOR);
            this.screen.setRegion(this.windows[0].shape);
            this.screen.gotoXY(this.windows[0].shape.x, this.windows[0].shape.y);
            this.windows[0].cursorPosition = this.screen.getCursorPosition();
            this.windows[1].cursorPosition = new Point(0, 1);
            this.statusBar.cursorPosition = new Point(0, 0);
            return;
        }

        if (this.version >= 4 && this.version <= 8 && this.version != 6) {
            // V4-5,7-8; Use an upper window and a lower window.
            this.windows = new ZaxWindow[2];
            this.windows[0] = new ZaxWindow(0, 1, this.screenSize.width, this.screenSize.height-1);
            this.windows[1] = new ZaxWindow(0, 0, this.screenSize.width,1);

            // Start off in window 0
            this.curWindow = this.windows[0];
            this.screen.setAttributes(TextScreen.KEYECHO | TextScreen.CURSOR);
            this.screen.setRegion(this.windows[0].shape);
            this.screen.gotoXY(this.windows[0].shape.x, this.windows[0].shape.y);
            this.windows[0].cursorPosition = this.screen.getCursorPosition();
            this.windows[1].cursorPosition = new Point(0, 0);
            return;
        }

        // Otherwise, this is an unsupported version.
        System.out.println("Unsupported storyfile version.");
        System.exit(1);
    }
    
    // Sets the terminating characters for READ operations (other than
    // CR).  Translates from Z-Characters to Event characters by
    // enumerating through the inputCharacter table.
    @Override
    public void setTerminatingCharacters(final Vector characters)
    {
        for (int i = 0; i < characters.size(); i++) {
           final Integer c = (Integer) characters.elementAt(i);

            // We don't bother using the Map containsValue() method--
            // that just makes this whole thing more expensive.
            // TODO replace the inputCharacters map with an Enum
            boolean found = false;
            for (final Map.Entry<Integer, Integer> entry: this.inputCharacters.entrySet())
            {
                if (entry.getValue().equals(c))
                {
                    this.terminatingCharacters.add(entry.getKey());
                    found = true;
                    break;
                }
            }
            
            if (!found)
            {
                this.terminatingCharacters.addElement(c);
            }
        }
        this.screen.setTerminators(this.terminatingCharacters);
    }

    // We support a status line in V1-3 only.
    @Override
    public boolean hasStatusLine()
    {
        return this.version >= 1 && this.version <= 3;
    }

    // We support an upper window starting at V3.
    @Override
    public boolean hasUpperWindow()
    {
        return this.version >= 3;
    }

    // For now, we always use a fixed-width font.
    @Override
    public boolean defaultFontProportional()
    {
        return false;
    }

	@Override
    public boolean hasFixedWidth()
	{
		return true;
	}

	// Yes, we have colors
	@Override
    public boolean hasColors()
	{
		return true;
	}

	// Yes, we have italic
	@Override
    public boolean hasItalic()
	{
		return true;
	}

	// Yes, we have boldface
	@Override
    public boolean hasBoldface()
	{
		return true;
	}

    // Yes, we have timed input
    @Override
    public boolean hasTimedInput()
    {
        return true;
    }
    
	// Our default background color is blue right now. FIX THIS
	@Override
    public int getDefaultBackground()
	{
		return 6;
	}

	// Our default foreground color is white for now
	@Override
    public int getDefaultForeground()
	{
		return 9;
	}

    // Show the status bar (it is guaranteed that this will only
    // be called during a V1-3 game).
    @Override
    public void showStatusBar(final String s, final int a, final int b, final boolean flag)
    {
        // This is kinda hacky for now.
        final ZaxWindow lastWindow = this.curWindow;
        switchWindow(this.statusBar);
        this.screen.reverseVideo(true);

        final StringBuilder statusLHS = new StringBuilder(" " + s + " ");
        final StringBuilder statusRHS = new StringBuilder();
        if (flag)
        {
            statusRHS.append(" Time: ").append(a).append(':');
            if (b < 10)
            {
                statusRHS.append('0');
            }
            statusRHS.append(b);
        }
        else
        {
            statusRHS.append(" Score: ").append(a).append(" ");
            statusRHS.append(" Turns: ").append(b).append(" ");
        }

        for (int i = 0; i < this.screenSize.width - (statusLHS.length() + statusRHS.length()); i++)
        {
            statusLHS.append(' ');
        }

        this.screen.printString(statusLHS.toString() + statusRHS.toString());

        this.screen.reverseVideo(false);
		switchWindow(lastWindow);
    }

    // Split the screen, as per SPLIT_SCREEN
    @Override
    public void splitScreen(final int n)
    {
        int lines = n;
        if (lines > this.screenSize.height)
        {
            lines = this.screenSize.height;
        }
        
        // Make sure the current cursor position is saved.
        this.curWindow.cursorPosition = this.screen.getCursorPosition();
        
        // Set window 1 to the desired size--it is always at (0,0).
        // Reposition the cursor if it is now outside the window.
        this.windows[1].shape = new Rectangle(0, 0, this.screenSize.width, lines);
        if (this.windows[1].cursorPosition.y >= this.windows[1].shape.height)
        {
            this.windows[1].cursorPosition = new Point(0, 0);
        }
        
        // Ditto for window 0, which always covers the bottom part of
        // the screen.
        this.windows[0].shape = new Rectangle(0, lines, this.screenSize.width, this.screenSize.height - lines);
        if (this.windows[0].cursorPosition.y < this.windows[0].shape.y)
        {
            this.windows[0].cursorPosition = new Point(0, this.windows[0].shape.y);
        }
        
        // Make sure the cursor and region are in the right place.
        this.screen.gotoXY(this.curWindow.cursorPosition.x, this.curWindow.cursorPosition.y);
        this.screen.setRegion(this.curWindow.shape);
    }
    
    // Set the current window, possibly clearing it.
    @Override
    public void setCurrentWindow(final int window)
    {
        switchWindow(this.windows[window]);
        if (window == 1)
        {
            if (this.version < 4)
            {
                this.screen.clearScreen();
            }
            this.screen.gotoXY(this.curWindow.shape.x, this.curWindow.shape.y);
        }
    }

    // Read a line of input from the current window.  If time is
    // nonzero, time out after time tenths of a second.  Return 0
    // if a timeout occurred, or the terminating character if it
    // did not.
    @Override
    public int readLine(final StringBuffer buffer, final int time)
    {
        this.moreLines = 0;
        this.screen.requestFocus();

        final int rc;
        if (time == 0)
        {
            rc = this.screen.readLine(buffer);
        }
        else
        {
            rc = this.screen.readLine(buffer, (long) (time * 100));
        }

        if (rc == -2)
        {
            fatal("Unspecified input error");
        }
        if (rc == -1)
        {
            return -1;
        }

        final Integer zc = this.inputCharacters.get(new Integer(rc));
        return zc != null ? zc.intValue() : rc;
    }

    // Read a single character from the current window
    @Override
    public int readChar(final int time)
    {
        this.moreLines = 0;
        this.screen.requestFocus();

        final int key;
        if (time == 0)
        {
            key = this.screen.readChar();
        }
        else
        {
            key = this.screen.readChar((long) (time * 100));
        }

        if (key == -2)
        {
            fatal("Unspecified input error");
        }
        if (key == -1)
        {
            return -1;
        }

        final Integer zchar = this.inputCharacters.get(new Integer(key));
        return zchar != null ? zchar.intValue() : key;
    }

    // Display a string -- this method does a number of things, including scrolling only
	// as necessary, word-wrapping, and "more".
    @Override
    public void showString(final String s)
    {
		// If this is not window 0, the output window, then we don't do any special
		// handling.  Is this correct?  Probably not in V6.
		if (this.curWindow != this.windows[0])
		{
            this.screen.printString(s);
			return;
		}

		// Get the current dimensions and cursor position of the screen.
        final Point cursor = this.screen.getCursorPosition();
		cursor.x -= this.curWindow.shape.x;
		cursor.y -= this.curWindow.shape.y;
		int curCol = cursor.x;

		int scrollLines; // We don't always do a more and a scroll at the same time.
		if (cursor.y < this.curWindow.shape.height - 1)
        {
            scrollLines = -(this.curWindow.shape.height - cursor.y - 1);
        }
		else
        {
            scrollLines = 0;
        }

		// Now, go through the string as a series of tokens.  Word-wrap, scroll, and
		// "more" as necessary.
		StringBuilder outstr = new StringBuilder();
        final StringTokenizer intokens = new StringTokenizer(s,"\n ",true);
		while (intokens.hasMoreTokens())
        {
			final String curtoken = intokens.nextToken();
			if (curtoken.equals("\n"))
			{
				outstr.append("\n");
				scrollLines++;
                this.moreLines++;
				curCol = 0;
				if (this.moreLines >= this.curWindow.shape.height - 1)
				{
                    this.screen.scrollUp(scrollLines);
					scrollLines = 0;
                    this.screen.printString(outstr.toString());
					outstr = new StringBuilder();
                    this.screen.reverseVideo(true);   // May need to check current state of screen
                    this.screen.printString("[--More--]");
                    this.screen.reverseVideo(false);
					final int oldattrs = this.screen.getAttributes();
                    this.screen.setAttributes(oldattrs & ~TextScreen.KEYECHO);
                    this.screen.requestFocus();
					this.screen.readChar(); // char ignored
                    this.screen.setAttributes(oldattrs);
                    this.screen.printString("\b\b\b\b\b\b\b\b\b\b");
                    this.moreLines = 0;
				}
				continue;
			}
			if (curtoken.length() + curCol > this.curWindow.shape.width - 2) // word wrap
			{
				if (curtoken.equals(" ")) // Skip spaces at ends of lines.
                {
                    continue;
                }
				outstr.append("\n");
				scrollLines++;
                this.moreLines++;
				curCol = 0;
				if (this.moreLines >= this.curWindow.shape.height - 1)
				{
                    this.screen.scrollUp(scrollLines);
					scrollLines = 0;
                    this.screen.printString(outstr.toString());
					outstr = new StringBuilder();
                    this.screen.reverseVideo(true);   // May need to check current state of screen
                    this.screen.printString("[--More--]");
                    this.screen.reverseVideo(false);
					final int oldattrs = this.screen.getAttributes();
                    this.screen.setAttributes(oldattrs & ~TextScreen.KEYECHO);
                    this.screen.requestFocus();
                    this.screen.readChar(); // char ignored
                    this.screen.setAttributes(oldattrs);
                    this.screen.printString("\b\b\b\b\b\b\b\b\b\b");
                    this.moreLines = 0;
				}
			}
			outstr.append(curtoken);
			curCol += curtoken.length();
		}

		// Output whatever's left.
		if (scrollLines > 0)
        {
            this.screen.scrollUp(scrollLines);
        }
		if (outstr.length() > 0)
        {
            this.screen.printString(outstr.toString());
        }
    }

    // Scroll the current window
    @Override
    public void scrollWindow(final int lines)
    {
        if (lines < 0)
        {
            fatal("Scroll down not yet implemented");
        }

        this.screen.scrollUp(lines);
    }

    // Erase a line in the current window
    @Override
    public void eraseLine(final int size)
    {
        fatal("eraseLine not yet implemented");
    }

	// Erase a window
	@Override
    public void eraseWindow(final int window)
    {
        final ZaxWindow lastWindow = this.curWindow;
	    switchWindow(this.windows[window]);
        this.screen.clearScreen();
		switchWindow(lastWindow);
	}

	// Get a filename for save or restore
	@Override
    public String getFilename(final String title, final String suggested, final boolean saveFlag)
    {
        final FileDialog fd = new FileDialog(this, title, saveFlag ? FileDialog.SAVE : FileDialog.LOAD);
        if (suggested != null)
        {
            fd.setFile(suggested);
        }
		else if (saveFlag)
        {
            fd.setFile("*.zav");
        }
		fd.setVisible(true);

        String s = fd.getFile();
		if (s == null)
        {
            return null;
        }
		if (s.length() == 0)
        {
            return null;
        }
		    
		// The Windows 95 peer sometimes appends crud to the filename.
        final int i = s.indexOf(".*", 0);
		if (i > -1)
        {
            s = s.substring(0, i);
        }
		return fd.getDirectory() + s;
	}

	// Return the cursor position, 1-based
	@Override
    public Point getCursorPosition()
	{
	    final Point p = this.screen.getCursorPosition();
	    p.x -= this.curWindow.shape.x;
	    p.y -= this.curWindow.shape.y;
	    p.x++;
	    p.y++;
	    return p;
	}

	// Set the cursor position (1-based)
	@Override
    public void setCursorPosition(int x, int y)
	{
	    x--;
	    y--;
	    x += this.curWindow.shape.x;
	    y += this.curWindow.shape.y;
        this.screen.gotoXY(x,y);
	}

	// Set the font
	@Override
    public void setFont(final int font)
	{
		System.out.println("Ignored a SET_FONT!");
	}

	// Return the size of the current font.
	@Override
    public Dimension getFontSize()
	{
        return this.screen.getFontSize();
	}

    // Return the size of the specified window
    @Override
    public Dimension getWindowSize(final int window)
    {
        return new Dimension(this.windows[window].shape.width, this.windows[window].shape.height);
    }
    
	// Set the current colors
	@Override
    public void setColor(final int foreground, final int background)
	{
		System.out.println("Ignored a SET_COLOR!");
	}

	// Set the text style
	@Override
    public void setTextStyle(final int style)
	{
        this.curWindow.tstyle = style;
	 
	    // If the style is 0, just clear everything
	    if (style == 0)
	    {
            this.screen.reverseVideo(false);
            this.screen.setFontStyle(Font.PLAIN);
	        return;
	    }
	    
	    // Otherwise, set things according to the bits we
	    // were passed.  Bit 3 (fixed-width) is currently
	    // unimplemented.
	    if ((style & 0x01) == 0x01)
        {
            this.screen.reverseVideo(true);
        }
	    if ((style & 0x02) == 0x02)
        {
            this.screen.setFontStyle(Font.BOLD);
        }
	    if ((style & 0x04) == 0x04)
        {
            this.screen.setFontStyle(Font.ITALIC);
        }
	    // Just ignore any styles we don't know about.
	}

	// Get the size of the current window in characters
	@Override
    public Dimension getScreenCharacters()
	{
		return this.screenSize;
	}

	// Get the size of the screen in units
	@Override
    public Dimension getScreenUnits()
	{
        final Dimension d = new Dimension();
        final Dimension fs = getFontSize();
        final Dimension scr = getScreenCharacters();
		d.width = scr.width * fs.width;
		d.height = scr.height * fs.height;
		return d;
	}

    // quit--end the program
    @Override
    public void quit()
    {
        final Thread curThread = this.cpuThread;
        this.cpuThread = null;
		curThread.stop();
    }

	// restart--prepare for a restart
	@Override
    public void restart()
	{
		initialize(this.version);
	}
}
