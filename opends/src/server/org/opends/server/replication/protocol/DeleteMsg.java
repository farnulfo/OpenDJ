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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import static org.opends.server.replication.protocol.OperationContext.*;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.ByteString;
import org.opends.server.types.operation.PostOperationDeleteOperation;

/**
 * Object used when sending delete information to replication servers.
 */
public class DeleteMsg extends LDAPUpdateMsg
{
  /**
   * Creates a new delete message.
   *
   * @param operation the Operation from which the message must be created.
   */
  public DeleteMsg(PostOperationDeleteOperation operation)
  {
    super((OperationContext) operation.getAttachment(SYNCHROCONTEXT),
           operation.getRawEntryDN().toString());
  }

  /**
   * Creates a new delete message.
   *
   * @param dn The dn with which the message must be created.
   * @param changeNumber The change number with which the message must be
   *                     created.
   * @param uid The unique id with which the message must be created.
   */
  public DeleteMsg(String dn, ChangeNumber changeNumber, String uid)
  {
    super(new DeleteContext(changeNumber, uid), dn);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid DeleteMsg
   * @throws UnsupportedEncodingException  If UTF8 is not supported by the jvm
   */
  public DeleteMsg(byte[] in) throws DataFormatException,
                                     UnsupportedEncodingException
  {
    byte[] allowedPduTypes = new byte[2];
    allowedPduTypes[0] = MSG_TYPE_DELETE;
    allowedPduTypes[1] = MSG_TYPE_DELETE_V1;
    int pos = decodeHeader(allowedPduTypes, in);

    // protocol version has been read as part of the header
    if (protocolVersion >= 4)
      decodeBody_V4(in, pos);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public AbstractOperation createOperation(
         InternalClientConnection connection, String newDn)
  {
    DeleteOperationBasis del =  new DeleteOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOf(newDn));
    DeleteContext ctx = new DeleteContext(getChangeNumber(), getUniqueId());
    del.setAttachment(SYNCHROCONTEXT, ctx);
    return del;
  }

  // ============
  // Msg encoding
  // ============

  /**
   * {@inheritDoc}
   */
  public byte[] getBytes_V1() throws UnsupportedEncodingException
  {
    return encodeHeader_V1(MSG_TYPE_DELETE_V1, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V23() throws UnsupportedEncodingException
  {
    return encodeHeader(MSG_TYPE_DELETE, 0,
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V4() throws UnsupportedEncodingException
  {
    // Put together the different encoded pieces
    int bodyLength = 0;

    byte[] byteEntryAttrLen =
      String.valueOf(encodedEclIncludes.length).getBytes("UTF-8");
    bodyLength += byteEntryAttrLen.length + 1;
    bodyLength += encodedEclIncludes.length + 1;

    /* encode the header in a byte[] large enough to also contain the mods */
    byte [] encodedMsg = encodeHeader(MSG_TYPE_DELETE, bodyLength,
        ProtocolVersion.REPLICATION_PROTOCOL_V4);
    int pos = encodedMsg.length - bodyLength;
    pos = addByteArray(byteEntryAttrLen, encodedMsg, pos);
    pos = addByteArray(encodedEclIncludes, encodedMsg, pos);
    return encodedMsg;
  }

  // ============
  // Msg decoding
  // ============

  private void decodeBody_V4(byte[] in, int pos)
  throws DataFormatException, UnsupportedEncodingException
  {
    // Read ecl attr len
    int length = getNextLength(in, pos);
    int eclAttrLen = Integer.valueOf(new String(in, pos, length,"UTF-8"));
    pos += length + 1;

    // Read/Don't decode entry attributes
    encodedEclIncludes = new byte[eclAttrLen];
    try
    {
      System.arraycopy(in, pos, encodedEclIncludes, 0, eclAttrLen);
    } catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (ArrayStoreException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (NullPointerException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "DeleteMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + uniqueId +
        " assuredFlag: " + assuredFlag;
    }
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      return "DeleteMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + uniqueId +
        " assuredFlag: " + assuredFlag +
        " assuredMode: " + assuredMode +
        " safeDataLevel: " + safeDataLevel;
    }
    return "!!! Unknown version: " + protocolVersion + "!!!";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return encodedEclIncludes.length + headerSize();
  }

}
