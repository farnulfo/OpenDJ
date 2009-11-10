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
package org.opends.server.replication.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.ServerConstants;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;



/**
 * This class implements a virtual attribute provider that allows administrators
 * to define their own values that will be inserted into any entry that matches
 * the criteria defined in the virtual attribute rule.  This can be used to
 * provide functionality like Class of Service (CoS) in the Sun Java System
 * Directory Server.
 */
public class LastChangeNumberVirtualAttributeProvider
       extends VirtualAttributeProvider<UserDefinedVirtualAttributeCfg>
       implements ConfigurationChangeListener<UserDefinedVirtualAttributeCfg>
{
  /**
   * Creates a new instance of this member virtual attribute provider.
   */
  public LastChangeNumberVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeVirtualAttributeProvider(
                            UserDefinedVirtualAttributeCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization required
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeVirtualAttributeProvider()
  {
    //
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMultiValued()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,VirtualAttributeRule rule)
  {
    Set<AttributeValue> values = new HashSet<AttributeValue>();
    String last = "0";
    try
    {
      if (!entry.getDN().equals(DN.decode("")))
      {
        return values;
      }
      ECLWorkflowElement eclwe = (ECLWorkflowElement)
      DirectoryServer.getWorkflowElement("EXTERNAL CHANGE LOG");
      if (eclwe!=null)
      {
        // Set a list of excluded domains (also exclude 'cn=changelog' itself)
        ArrayList<String> excludedDomains =
          MultimasterReplication.getECLDisabledDomains();
        if (!excludedDomains.contains(
            ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT))
          excludedDomains.add(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);

        ReplicationServer rs = eclwe.getReplicationServer();
        int[] limits = rs.getECLDraftCNLimits(
            rs.getEligibleCN(), excludedDomains);

        last = String.valueOf(limits[1]);
      }
    }
    catch(Exception e)
    {

    }
    AttributeValue value =
      AttributeValues.create(
          ByteString.valueOf(last),
          ByteString.valueOf(last));
    values=Collections.singleton(value);
    return values;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation)
  {
    // We will not allow searches based only on user-defined virtual attributes.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    return;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      UserDefinedVirtualAttributeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 UserDefinedVirtualAttributeCfg configuration)
  {
    return new ConfigChangeResult(ResultCode.OTHER, false);
  }
}

