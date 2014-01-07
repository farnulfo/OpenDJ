/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.server.tools.dsreplication;

import java.util.LinkedList;
import java.util.List;

/**
 * This class is used to store the information provided by the user in the
 * replication command line.  It is required because when we are in interactive
 * mode the ReplicationCliArgumentParser is not enough.
 *
 */
public abstract class ReplicationUserData
{
  private final LinkedList<String> baseDNs = new LinkedList<String>();
  private String adminUid;
  private String adminPwd;

  /**
   * Returns the Global Administrator password.
   * @return the Global Administrator password.
   */
  public String getAdminPwd()
  {
    return adminPwd;
  }

  /**
   * Sets the Global Administrator password.
   * @param adminPwd the Global Administrator password.
   */
  public void setAdminPwd(String adminPwd)
  {
    this.adminPwd = adminPwd;
  }

  /**
   * Returns the Global Administrator UID.
   * @return the Global Administrator UID.
   */
  public String getAdminUid()
  {
    return adminUid;
  }

  /**
   * Sets the Global Administrator UID.
   * @param adminUid the Global Administrator UID.
   */
  public void setAdminUid(String adminUid)
  {
    this.adminUid = adminUid;
  }

  /**
   * Returns the Base DNs to replicate.
   * @return the Base DNs to replicate.
   */
  public List<String> getBaseDNs()
  {
    return new LinkedList<String>(baseDNs);
  }

  /**
   * Sets the Base DNs to replicate.
   * @param baseDNs the Base DNs to replicate.
   */
  public void setBaseDNs(List<String> baseDNs)
  {
    this.baseDNs.clear();
    this.baseDNs.addAll(baseDNs);
  }
}
