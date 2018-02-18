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
package com.zaxsoft.zmachine;

import java.io.*;

class ZMemory extends Object {
    private ZUserInterface zui;
    byte[] data;
    int dataLength;

    // The initialize routine sets things up and loads a game
    // into memory.  It is passed the ZUserInterface object
    // for this ZMachine and the filename of the story-file.
    void initialize(ZUserInterface ui,String storyFile)
    {
        File f;
        FileInputStream fis;
        DataInputStream dis;
        this.zui = ui;

        // Read in the story file
        f = new File(storyFile);
        if(!f.exists() || !f.canRead() || !f.isFile())
            this.zui.fatal("Storyfile " + storyFile + " not found.");
        this.dataLength = (int)f.length();
        this.data = new byte[this.dataLength];
        try {
            fis = new FileInputStream(f);
            dis = new DataInputStream(fis);
            dis.readFully(this.data,0, this.dataLength);
            fis.close();
        }
        catch (IOException ioex) {
            this.zui.fatal("I/O error loading storyfile.");
        }
    }

    // Fetch a byte from the specified address
    int fetchByte(int addr)
    {
        if (addr > this.dataLength - 1)
            this.zui.fatal("Memory fault: address " + addr);
        int i = this.data[addr] & 0xff;
        return i;
    }

    // Store a byte at the specified address
    void putByte(int addr,int b)
    {
        if (addr > this.dataLength - 1)
            this.zui.fatal("Memory fault: address " + addr);
        this.data[addr] = (byte)(b & 0xff);
    }

    // Fetch a word from the specified address
    int fetchWord(int addr)
    {
        int i;

        if (addr > this.dataLength - 1)
            this.zui.fatal("Memory fault: address " + addr);
        i = (this.data[addr] << 8 | this.data[addr+1] & 0xff) & 0xffff;
        return i;
    }

    // Store a word at the specified address
    void putWord(int addr,int w)
    {
        if (addr > this.dataLength - 1)
            this.zui.fatal("Memory fault: address " + addr);
        this.data[addr] = (byte)(w >> 8 & 0xff);
        this.data[addr+1] = (byte)(w & 0xff);
    }

	// Dump the specified amount of memory, starting at the specified address,
	// to the specified DataOutputStream.
	void dumpMemory(DataOutputStream dos,int addr,int len) throws IOException
	{
		dos.write(this.data,addr,len);
	}

	// Read in memory stored by dumpMemory.
	void readMemory(DataInputStream dis,int addr,int len) throws IOException
	{
		dis.read(this.data,addr,len);
	}
}
