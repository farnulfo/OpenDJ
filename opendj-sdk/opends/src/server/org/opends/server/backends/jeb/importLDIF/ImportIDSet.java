/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb.importLDIF;

import org.opends.server.backends.jeb.EntryID;
import org.opends.server.backends.jeb.JebFormat;


/**
 * An import ID set backed by an array of ints.
 */
public class ImportIDSet {

  /**
   * The internal array where elements are stored.
   */
  private long[] array = null;


  /**
   * The number of valid elements in the array.
   */
  private int count = 0;


  //Boolean to keep track if the instance is defined or not.
  private boolean isDefined=true;


  //Size of the undefines.
  private long undefinedSize = 0;

  //Key related to an ID set.
  private byte[] key;
  private int limit;
  private boolean doCount;


  /**
   * Create an import ID set of the specified size, index limit and index
   * maintain count boolean, plus an extra 128 slots.
   *
   * @param size The size of the the underlying array, plus some extra space.
   * @param limit The index entry limit.
   * @param doCount The index maintain count boolean.
   */
  public ImportIDSet(int size, int limit, boolean doCount)
  {
    this.array = new long[size + 128];
    this.limit = limit;
    this.doCount = doCount;
  }


  /**
   * Return if an import ID set is defined or not.
   *
   * @return <CODE>True</CODE> if an import ID set is defined.
   */
  public boolean isDefined()
  {
    return isDefined;
  }

  /**
   * Return the undefined size of an import ID set.
   *
   * @return The undefined size of an import ID set.
   */
  long getUndefinedSize()
  {
    return undefinedSize;
  }

  /**
   * Set an import ID set to undefined.
   */
  void setUndefined() {
    array = null;
    isDefined = false;
  }


  /**
   * Merge an instance of an import ID set with the import ID set specified
   * in the parameter. The specified limit and maintain count parameters define
   * if the newly merged set is defined or not.
   *
   * @param importIDSet The import ID set to merge with.
   */
  public void
  merge(ImportIDSet importIDSet)
  {
    if(!isDefined() && !importIDSet.isDefined()) //both undefined
    {
      if(doCount)
      {
        undefinedSize += importIDSet.getUndefinedSize();
      }
      return;
    }
    else if(!isDefined()) //this undefined
    {
      if(doCount)
      {
          undefinedSize += importIDSet.size();
      }
      return;
    }
    else if(!importIDSet.isDefined()) //other undefined
    {
      isDefined = false;
      if(doCount)
      {
        undefinedSize =  size() + importIDSet.getUndefinedSize();
      } else {
        undefinedSize = Long.MAX_VALUE;
      }
      array = null;
      count = 0;
    }
    else if ((count + importIDSet.size()) > limit) //add together => undefined
    {
      isDefined = false;
      if(doCount)  {
        undefinedSize = size() + importIDSet.size();
      } else {
        undefinedSize = Long.MAX_VALUE;
      }
      array = null;
      count = 0;
    } else {
      addAll(importIDSet);
    }
  }


  /**
   * Add the specified entry id to an import ID set.
   *
   * @param entryID  The entry ID to add to an import ID set.
   */
  public void addEntryID(EntryID entryID) {
    addEntryID(entryID.longValue());
  }

    /**
   * Add the specified long value to an import ID set.
   *
   * @param l The long value to add to an import ID set.
   */
  public void addEntryID(long l) {
    if(!isDefined()) {
      if(doCount)  {
        undefinedSize++;
      }
      return;
    }
    if(isDefined() && ((count + 1) > limit)) {
      isDefined = false;
      array = null;
      if(doCount)  {
        undefinedSize = count + 1;
      } else {
        undefinedSize = Long.MAX_VALUE;
      }
      count = 0;
    } else {
      add(l);
    }
  }


  private boolean
  mergeCount(byte[] dBbytes, ImportIDSet importIdSet)  {
    boolean incrLimitCount=false;
    boolean dbUndefined = ((dBbytes[0] & 0x80) == 0x80);

    if(dbUndefined && (!importIdSet.isDefined()))  {
       undefinedSize = JebFormat.entryIDUndefinedSizeFromDatabase(dBbytes) +
                                                 importIdSet.getUndefinedSize();
       isDefined=false;
    } else if(dbUndefined && (importIdSet.isDefined()))  {
       undefinedSize = JebFormat.entryIDUndefinedSizeFromDatabase(dBbytes) +
                                                 importIdSet.size();
       importIdSet.setUndefined();
       isDefined=false;
    } else if(!importIdSet.isDefined()) {
       int dbSize = JebFormat.entryIDListFromDatabase(dBbytes).length;
       undefinedSize= dbSize + importIdSet.getUndefinedSize();
       isDefined=false;
       incrLimitCount = true;
    } else {
      array = JebFormat.entryIDListFromDatabase(dBbytes);
      if(array.length + importIdSet.size() > limit) {
          undefinedSize = array.length + importIdSet.size();
          importIdSet.setUndefined();
          isDefined=false;
          incrLimitCount=true;
      } else {
        count = array.length;
        addAll(importIdSet);
      }
    }
    return incrLimitCount;
  }


  /**
   * Remove the specified import ID set from the byte array read from the DB.
   *
   * @param dBbytes The byte array read from JEB.
   * @param importIdSet The import ID set to delete.
   */
  public void remove(byte[] dBbytes, ImportIDSet importIdSet)
  {
    boolean incrLimitCount=false;
    boolean dbUndefined = ((dBbytes[0] & 0x80) == 0x80);
    if(dbUndefined) {
      isDefined=false;
      importIdSet.setUndefined();
      undefinedSize = Long.MAX_VALUE;
    } else if(!importIdSet.isDefined()) {
      isDefined=false;
      incrLimitCount=true;
      undefinedSize = Long.MAX_VALUE;
    } else {
      array = JebFormat.entryIDListFromDatabase(dBbytes);
      if(array.length - importIdSet.size() > limit) {
        isDefined=false;
        incrLimitCount=true;
        count = 0;
        importIdSet.setUndefined();
        undefinedSize = Long.MAX_VALUE;
      } else {
        count = array.length;
        removeAll(importIdSet);
      }
    }
  }




  /**
   * Merge the specified byte array read from a DB, with the specified import
   * ID set. The specified limit and maintain count parameters define
   * if the newly merged set is defined or not.
   *
   * @param dBbytes The byte array of IDs read from a DB.
   * @param importIdSet The import ID set to merge the byte array with.
   * @return <CODE>True</CODE> if the import ID set started keeping a count as
   *         a result of the merge.
   */
  public boolean merge(byte[] dBbytes, ImportIDSet importIdSet)
  {
    boolean incrLimitCount=false;
    if(doCount) {
      incrLimitCount = mergeCount(dBbytes,  importIdSet);
    } else {
      boolean dbUndefined = ((dBbytes[0] & 0x80) == 0x80);
      if(dbUndefined) {
        isDefined=false;
        importIdSet.setUndefined();
        undefinedSize = Long.MAX_VALUE;
      } else if(!importIdSet.isDefined()) {
        isDefined=false;
        incrLimitCount=true;
        undefinedSize = Long.MAX_VALUE;
      } else {
        array = JebFormat.entryIDListFromDatabase(dBbytes);
        if(array.length + importIdSet.size() > limit) {
          isDefined=false;
          incrLimitCount=true;
          count = 0;
          importIdSet.setUndefined();
          undefinedSize = Long.MAX_VALUE;
        } else {
          count = array.length;
          addAll(importIdSet);
        }
      }
    }
    return incrLimitCount;
  }


  private void removeAll(ImportIDSet that) {

    long[] newArray = new long[array.length];
    int c = 0;
    for(int i=0; i < count; i++)
    {
      int rc = binarySearch(that.array, that.count, array[i]);
      if(rc < 0)
      {
        newArray[c++] = array[i];
      }
    }
    array = newArray;
    count = c;
  }



  private  void addAll(ImportIDSet that) {
    resize(this.count+that.count);

    if (that.count == 0)
    {
      return;
    }

    // Optimize for the case where the two sets are sure to have no overlap.
    if (this.count == 0 || that.array[0] > this.array[this.count-1])
    {
      System.arraycopy(that.array, 0, this.array, this.count, that.count);
      count += that.count;
      return;
    }

    if (this.array[0] > that.array[that.count-1])
    {
      System.arraycopy(this.array, 0, this.array, that.count, this.count);
      System.arraycopy(that.array, 0, this.array, 0, that.count);
      count += that.count;
      return;
    }

    int destPos = binarySearch(this.array, this.count, that.array[0]);
    if (destPos < 0)
    {
      destPos = -(destPos+1);
    }

    // Make space for the copy.
    int aCount = this.count - destPos;
    int aPos = destPos + that.count;
    int aEnd = aPos + aCount;
    System.arraycopy(this.array, destPos, this.array, aPos, aCount);

    // Optimize for the case where there is no overlap.
    if (this.array[aPos] > that.array[that.count-1])
    {
      System.arraycopy(that.array, 0, this.array, destPos, that.count);
      count += that.count;
      return;
    }

    int bPos;
    for ( bPos = 0; aPos < aEnd && bPos < that.count; )
    {
      if ( this.array[aPos] < that.array[bPos] )
      {
        this.array[destPos++] = this.array[aPos++];
      }
      else if ( this.array[aPos] > that.array[bPos] )
      {
        this.array[destPos++] = that.array[bPos++];
      }
      else
      {
        this.array[destPos++] = this.array[aPos++];
        bPos++;
      }
    }

    // Copy any remainder.
    int aRemain = aEnd - aPos;
    if (aRemain > 0)
    {
      System.arraycopy(this.array, aPos, this.array, destPos, aRemain);
      destPos += aRemain;
    }

    int bRemain = that.count - bPos;
    if (bRemain > 0)
    {
      System.arraycopy(that.array, bPos, this.array, destPos, bRemain);
      destPos += bRemain;
    }

    count = destPos;
  }


  /**
   * Return the number of IDs in an import ID set.
   *
   * @return The current size of an import ID set.
   */
  public int size()
  {
    return count;
  }


  private boolean add(long v)
  {
    resize(count+1);

    if (count == 0 || v > array[count-1])
    {
      array[count++] = v;
      return true;
    }

    int pos = binarySearch(array, count, v);
    if (pos >=0)
    {
      return false;
    }

    // For a negative return value r, the index -(r+1) gives the array
    // index at which the specified value can be inserted to maintain
    // the sorted order of the array.
    pos = -(pos+1);

    System.arraycopy(array, pos, array, pos+1, count-pos);
    array[pos] = v;
    count++;
    return true;
  }


  private static int binarySearch(long[] a, int count, long key)
  {
    int low = 0;
    int high = count-1;

    while (low <= high)
    {
      int mid = (low + high) >> 1;
      long midVal = a[mid];

      if (midVal < key)
        low = mid + 1;
      else if (midVal > key)
        high = mid - 1;
      else
        return mid; // key found
    }
    return -(low + 1);  // key not found.
  }



  private void resize(int size)
  {
    if (array == null)
    {
      array = new long[size];
    }
    else if (array.length < size)
    {
      // Expand the size of the array in powers of two.
      int newSize = array.length == 0 ? 1 : array.length;
      do
      {
        newSize *= 2;
      } while (newSize < size);

      long[] newBytes = new long[newSize];
      System.arraycopy(array, 0, newBytes, 0, count);
      array = newBytes;
    }

  }


  /**
   * Create a byte array suitable to write to a JEB DB from an import ID set.
   *
   * @return A byte array suitable for writing to a JEB DB.
   */
  public byte[] toDatabase()
  {
    if(isDefined) {
       return encode(null);
     } else {
       return JebFormat.entryIDUndefinedSizeToDatabase(undefinedSize);
     }
   }


  private byte[] encode(byte[] bytes)
  {
    int encodedSize = count * 8;
    if (bytes == null || bytes.length < encodedSize) {
      bytes = new byte[encodedSize];
    }
    for (int pos = 0, i = 0; i < count; i++) {
      long v = array[i] & 0x00ffffffffL;
      bytes[pos++] = (byte) ((v >>> 56) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 48) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 40) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 32) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 24) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 16) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 8) & 0xFF);
      bytes[pos++] = (byte) (v & 0xFF);
    }
    return bytes;
  }

  /**
   * Set the DB key related to an import ID set.
   *
   * @param key Byte array containing the key.
   */
  public void setKey(byte[] key)
  {
    this.key = key;
  }

  /**
   * Return the DB key related to an import ID set.
   *
   * @return  The byte array containing the key.
   */
  public byte[] getKey()
  {
    return key;
  }
}
