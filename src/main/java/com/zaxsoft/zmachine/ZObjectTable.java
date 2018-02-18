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

/**
 * ZObjectTable - Encapsulation of the Z-Machine object table.
 *
 * @author Matt Kimmel
 */
class ZObjectTable extends Object {
    // Local variables
    private ZUserInterface zui; // User interface object
    private ZMemory memory; // This Z-Machine's memory object
    private int version; // Version number of current storyfile
    private int objTable; // Address of object table
    private int defaultsSize; // Size of property default table, in bytes
    private int objAttrSize; // Size of an object's attribute table, in bytes
    private int objHandleSize; // Size of an object handle, in bytes
    private int objEntrySize; // Size of an object entry in the table

    // The initialize routine passes a handle to the Z-Machine's
    // memory, as well as to the user interface and to the version
    // of the current storyfile.
    void initialize(ZUserInterface ui,ZMemory mem,int ver)
    {
        this.zui = ui;
        this.memory = mem;
        this.version = ver;

        this.objTable = this.memory.fetchWord(0x0a);
        if (this.version <= 3) {
            this.defaultsSize = 62;
            this.objAttrSize = 4;
            this.objHandleSize = 1;
        }
        else {
            this.defaultsSize = 126;
            this.objAttrSize = 6;
            this.objHandleSize = 2;
        }
        this.objEntrySize = this.objAttrSize + 3 * this.objHandleSize + 2;
    }

    /////////////////////////////////////////////////////////////////
    // Object manipulation routines
    /////////////////////////////////////////////////////////////////

    // Return the sibling of an object
    int getSibling(int obj)
    {
        int sib;

        if (this.version <= 3)
            sib = this.memory.fetchByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + this.objHandleSize);
        else
            sib = this.memory.fetchWord(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + this.objHandleSize);

        return sib;
    }

    // Set the sibling of an object
    void setSibling(int obj,int sib)
    {
        if (this.version <= 3)
            this.memory.putByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + this.objHandleSize,sib);
        else
            this.memory.putWord(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + this.objHandleSize,sib);
    }

    // Return the first child of an object
    int getChild(int obj)
    {
        int child;

        if (this.version <= 3)
            child = this.memory.fetchByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + 2 * this.objHandleSize);
        else
            child = this.memory.fetchWord(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + 2 * this.objHandleSize);

        return child;
    }

    // Set the child of an object
    void setChild(int obj,int child)
    {
        if (this.version <= 3)
            this.memory.putByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + 2 * this.objHandleSize,child);
        else
            this.memory.putWord(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + 2 * this.objHandleSize,child);
    }

    // Return an object's parent
    int getParent(int obj)
    {
        int parent;

        if (this.version <= 3)
            parent = this.memory.fetchByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize);
        else
            parent = this.memory.fetchWord(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize);

        return parent;
    }

    // Set the parent of an object
    void setParent(int obj,int parent)
    {
        if (this.version <= 3)
            this.memory.putByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize,parent);
        else
            this.memory.putWord(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize,parent);
    }

    // Given its (non-zero) parent, remove an object from the
    // sibling chain.
    void removeObject(int parent,int obj)
    {
        int curObj, prevObj;

		// It is legal for parent to be 0, in which case we just return.
		if (parent == 0)
			return;

        curObj = getChild(parent);
        if (curObj == 0)
            this.zui.fatal("Corrupted object table");
        if (curObj == obj) {
            // Remove the object
            setChild(parent,getSibling(obj));
			setSibling(obj,0);
			setParent(obj,0);
            return;
        }

        // Traverse the sibling chain until we find the object
        // and its predecessor.
        prevObj = curObj;
        curObj = getSibling(prevObj);
        while (curObj != obj && curObj != 0) {
            prevObj = curObj;
            curObj = getSibling(prevObj);
        }

        // If we get here, curObj is either the object we're looking
        // for or 0 (which is an error).
        if (curObj == 0)
            this.zui.fatal("Corrupted object table");

        // Remove the object from the chain, and set its sibling and parent to 0.
        setSibling(prevObj,getSibling(curObj));
        setSibling(obj,0); // Is this necessary?
		setParent(obj,0);
    }


    // Insert obj1 as obj2's first child.
    void insertObject(int obj1,int obj2)
    {
        int oldparent;
        int oldfirst;

        // First, remove the given object from its current
        // position (if any).
        oldparent = getParent(obj1);
        if (oldparent > 0)
            removeObject(oldparent,obj1);

        // Now insert it.
        oldfirst = getChild(obj2);
        setSibling(obj1,oldfirst);
        setChild(obj2,obj1);
		setParent(obj1,obj2);
    }

    //////////////////////////////////////////////////////////////
    // Property manipulation routines
    //////////////////////////////////////////////////////////////

    // Return the length of the property starting at the given
    // byte address
    int getPropertyLength(int baddr)
    {
        int b;
        int length;

        b = this.memory.fetchByte(baddr-1);
        if (this.version < 4)
            length = (b >> 5 & 0x07) + 1;
        else {
            if ((b & 0x80) == 0x80)
                length = b & 0x3f;
            else
                length = (b >> 6 & 0x01) + 1;
        }
        return length;
    }

    // Get the address of the property list for the specified object.
    int getPropertyList(int obj)
    {
        int addr;

        addr = this.memory.fetchWord(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + this.objAttrSize + 3 * this.objHandleSize);
        return addr;
    }

    // Get the address of the specified property of the specified
    // object.  Return 0x0000 if there is no such property.
    int getPropertyAddress(int obj,int prop)
    {
        int p;
        int s;
		int o;
        int pnum;
        int psize;

        // First, get the address of the property table for this
        // object.
        p = getPropertyList(obj);

        // Now step through, looking for the specified property.
        // Start by jumping over text header.
		o = this.memory.fetchByte(p);
        p = p + o * 2 + 1;

        // Now we're at the start of the property table.
        s = this.memory.fetchByte(p);
        while (s != 0) {
            // Get the property number and the size of this property.
            if (this.version < 4) {
                pnum = s & 0x1f;
                psize = (s >> 5 & 0x07) + 1;
            }
            else {
                pnum = s & 0x3f;
                if ((s & 0x80) == 0x80) {
                    p++;
                    psize = this.memory.fetchByte(p);
                    psize = psize & 0x3f;
                }
                else
                    psize = (s >> 6 & 0x03) + 1;
            }

            // Step over the size byte
            p++;

            // If this is the correct property, return its address;
            // otherwise, step over the property and loop.
            if (pnum == prop)
                return p;
            else
                p = p + psize;
			s = this.memory.fetchByte(p);
        }

        // If we make it here, the property was not found.
        return 0;
    }

    // Get the first byte or word of a property--use the default
    // property if this one doesn't exist.
    int getProperty(int obj,int prop)
    {
        int pdata;

        // Attempt to get the address of this property for this
        // object.
        pdata = getPropertyAddress(obj,prop);

        // If the property exists, return it; otherwise, return
        // the appropriate value from the defaults table.
        if (pdata > 0) {
            if (getPropertyLength(pdata) == 1)
                return this.memory.fetchByte(pdata);
            else
                return this.memory.fetchWord(pdata);
        }
        else
            return this.memory.fetchWord(this.objTable + (prop - 1) * 2);
    }

    // Return the property number of the property that follows
    // the specified property in the property list, or 0 if
    // the specified property doesn't exist.
    int getNextProperty(int obj,int prop)
    {
        int propaddr;
        int proplen;
        int propnum;

        // First, if the property number is 0, just return the
        // number of the first property of this object.
        if (prop == 0) {
            propaddr = getPropertyList(obj);
            // Skip over text-length byte and text header
            propaddr = propaddr + 1 + this.memory.fetchByte(propaddr) * 2;
            // Return the number of the first property.
            // This will work if the property number is 0, too.
            if (this.version < 4)
                propnum = this.memory.fetchByte(propaddr) & 0x1f;
            else
                propnum = this.memory.fetchByte(propaddr) & 0x3f;
            return propnum;
        }

        // First, get the address of the specified property.
        // If it doesn't exist, return 0.
        propaddr = getPropertyAddress(obj,prop);
        if (propaddr == 0)
            return 0;

        // Now find out its length.
        proplen = getPropertyLength(propaddr);

        // Skip over the property data
        propaddr += proplen;

        // Now return the number of the next property.  This will
        // return 0 if the property is a 0 byte.
        if (this.version < 4)
            propnum = this.memory.fetchByte(propaddr) & 0x1f;
        else
            propnum = this.memory.fetchByte(propaddr) & 0x3f;
        return propnum;
    }

    // Return the address of the Z-String containing the specified
    // object's name.
    int getObjectName(int obj)
    {
        int addr;

        addr = getPropertyList(obj) + 1;
        return addr;
    }

    // Put the specified value as the specified property of the
    // specified object.
    void putProperty(int obj,int prop,int value)
    {
        int propaddr;
        int proplen;

        // First, get the address of this property.  Fail silently
        // if the property does not exist.
        propaddr = getPropertyAddress(obj,prop);
        if (propaddr == 0)
            return;

        // Now set the property, depending on its length.
        proplen = getPropertyLength(propaddr);
        if (proplen == 1)
            this.memory.putByte(propaddr, value & 0xff);
        else
            this.memory.putWord(propaddr,value);
    }


    //////////////////////////////////////////////////////////////
    // Attribute manipulation routines
    //////////////////////////////////////////////////////////////

    // Return true if the specified object has the specified
    // attribute; otherwise return false.
    boolean hasAttribute(int obj,int attr)
    {
        int whichbyte;
        int whichbit;
        int bitmask;
        int attrbyte;

        // First, figure out which byte and bit we're looking at.
        whichbyte = attr / 8;
        whichbit = attr % 8;

        // Flip the bit number around to something we can use in
        // an AND.
        bitmask = 0x80 >>> whichbit;

        // Now get the appropriate byte and test it.
        attrbyte = this.memory.fetchByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + whichbyte);
        return (attrbyte & bitmask) == bitmask;
    }

    // Set an attribute on an object.
    void setAttribute(int obj,int attr)
    {
        int whichbyte;
        int whichbit;
        int bitmask;
        int attrbyte;

        // First, figure out which byte and bit we're looking at.
        whichbyte = attr / 8;
        whichbit = attr % 8;

        // Flip the bit number around to something we can use in
        // an OR.
        bitmask = 0x80 >>> whichbit;

        // Now get the appropriate byte and set it.
        attrbyte = this.memory.fetchByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + whichbyte);
        attrbyte = attrbyte | bitmask;
        this.memory.putByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + whichbyte,attrbyte);
    }

    // Clear an attribute on an object.
    void clearAttribute(int obj,int attr)
    {
        int whichbyte;
        int whichbit;
        int bitmask;
        int attrbyte;

        // First, figure out which byte and bit we're looking at.
        whichbyte = attr / 8;
        whichbit = attr % 8;

        // Flip the bit number around to something we can use in
        // a NOT.
        bitmask = 0x80 >>> whichbit;

        // Now get the appropriate byte and clear it.
        attrbyte = this.memory.fetchByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + whichbyte);
        attrbyte = attrbyte & ~bitmask & 0xff;
        this.memory.putByte(this.objTable + this.defaultsSize + (obj - 1) * this.objEntrySize + whichbyte,attrbyte);
    }

}
