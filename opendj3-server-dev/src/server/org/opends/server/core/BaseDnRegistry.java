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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.api.Backend;
import static org.opends.server.util.Validator.ensureNotNull;
import org.opends.messages.Message;
import static org.opends.messages.CoreMessages.*;

import java.util.TreeMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;

/**
 * Registry for maintaining the set of registered base DN's, assocated
 * backends and naming context information.
 */
public class BaseDnRegistry {

  // The set of base DNs registered with the server.
  private TreeMap<DN,Backend> baseDNs;

  // The set of private naming contexts registered with the server.
  private TreeMap<DN,Backend> privateNamingContexts;

  // The set of public naming contexts registered with the server.
  private TreeMap<DN,Backend> publicNamingContexts;

  // Indicates whether or not this base DN registry is in test mode.
  // A registry instance that is in test mode will not modify backend
  // objects referred to in the above maps.
  private boolean testOnly;

  /**
   * Registers a base DN with this registry.
   *
   * @param  baseDN to register
   * @param  backend with which the base DN is assocated
   * @param  isPrivate indicates whether or not this base DN is private
   * @return list of error messages generated by registering the base DN
   *         that should be logged if the changes to this registry are
   *         committed to the server
   * @throws DirectoryException if the base DN cannot be registered
   */
  public List<Message> registerBaseDN(DN baseDN, Backend backend,
                                      boolean isPrivate)
          throws DirectoryException
  {

    List<Message> errors = new LinkedList<Message>();

    // Check to see if the base DN is already registered with the server.
    Backend existingBackend = baseDNs.get(baseDN);
    if (existingBackend != null)
    {
      Message message = ERR_REGISTER_BASEDN_ALREADY_EXISTS.
          get(String.valueOf(baseDN), backend.getBackendID(),
              existingBackend.getBackendID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Check to see if the backend is already registered with the server for
    // any other base DN(s).  The new base DN must not have any hierarchical
    // relationship with any other base Dns for the same backend.
    LinkedList<DN> otherBaseDNs = new LinkedList<DN>();
    for (DN dn : baseDNs.keySet())
    {
      Backend b = baseDNs.get(dn);
      if (b.equals(backend))
      {
        otherBaseDNs.add(dn);

        if (baseDN.isAncestorOf(dn) || baseDN.isDescendantOf(dn))
        {
          Message message = ERR_REGISTER_BASEDN_HIERARCHY_CONFLICT.
              get(String.valueOf(baseDN), backend.getBackendID(),
                  String.valueOf(dn));
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       message);
        }
      }
    }


    // Check to see if the new base DN is subordinate to any other base DN
    // already defined.  If it is, then any other base DN(s) for the same
    // backend must also be subordinate to the same base DN.
    Backend superiorBackend = null;
    DN      superiorBaseDN        ;
    DN      parentDN        = baseDN.getParent();
    while (parentDN != null)
    {
      if (baseDNs.containsKey(parentDN))
      {
        superiorBaseDN  = parentDN;
        superiorBackend = baseDNs.get(parentDN);

        for (DN dn : otherBaseDNs)
        {
          if (! dn.isDescendantOf(superiorBaseDN))
          {
            Message message = ERR_REGISTER_BASEDN_DIFFERENT_PARENT_BASES.
                get(String.valueOf(baseDN), backend.getBackendID(),
                    String.valueOf(dn));
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }

        break;
      }

      parentDN = parentDN.getParent();
    }

    if (superiorBackend == null)
    {
      if (backend.getParentBackend() != null)
      {
        Message message = ERR_REGISTER_BASEDN_NEW_BASE_NOT_SUBORDINATE.
            get(String.valueOf(baseDN), backend.getBackendID(),
                backend.getParentBackend().getBackendID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     message);
      }
    }


    // Check to see if the new base DN should be the superior base DN for any
    // other base DN(s) already defined.
    LinkedList<Backend> subordinateBackends = new LinkedList<Backend>();
    LinkedList<DN>      subordinateBaseDNs  = new LinkedList<DN>();
    for (DN dn : baseDNs.keySet())
    {
      Backend b = baseDNs.get(dn);
      parentDN = dn.getParent();
      while (parentDN != null)
      {
        if (parentDN.equals(baseDN))
        {
          subordinateBaseDNs.add(dn);
          subordinateBackends.add(b);
          break;
        }
        else if (baseDNs.containsKey(parentDN))
        {
          break;
        }

        parentDN = parentDN.getParent();
      }
    }


    // If we've gotten here, then the new base DN is acceptable.  If we should
    // actually apply the changes then do so now.

    // Check to see if any of the registered backends already contain an
    // entry with the DN specified as the base DN.  This could happen if
    // we're creating a new subordinate backend in an existing directory
    // (e.g., moving the "ou=People,dc=example,dc=com" branch to its own
    // backend when that data already exists under the "dc=example,dc=com"
    // backend).  This condition shouldn't prevent the new base DN from
    // being registered, but it's definitely important enough that we let
    // the administrator know about it and remind them that the existing
    // backend will need to be reinitialized.
    if (superiorBackend != null)
    {
      if (superiorBackend.entryExists(baseDN))
      {
        Message message = WARN_REGISTER_BASEDN_ENTRIES_IN_MULTIPLE_BACKENDS.
            get(superiorBackend.getBackendID(), String.valueOf(baseDN),
                backend.getBackendID());
        errors.add(message);
      }
    }


    baseDNs.put(baseDN, backend);

    if (superiorBackend == null)
    {
      if (isPrivate)
      {
        if (!testOnly)
        {
          backend.setPrivateBackend(true);
        }
        privateNamingContexts.put(baseDN, backend);
      }
      else
      {
        if (!testOnly)
        {
          backend.setPrivateBackend(false);
        }
        publicNamingContexts.put(baseDN, backend);
      }
    }
    else if (otherBaseDNs.isEmpty())
    {
      if (!testOnly)
      {
        backend.setParentBackend(superiorBackend);
        superiorBackend.addSubordinateBackend(backend);
      }
    }

    if (!testOnly)
    {
      for (Backend b : subordinateBackends)
      {
        Backend oldParentBackend = b.getParentBackend();
        if (oldParentBackend != null)
        {
          oldParentBackend.removeSubordinateBackend(b);
        }

        b.setParentBackend(backend);
        backend.addSubordinateBackend(b);
      }
    }

    for (DN dn : subordinateBaseDNs)
    {
      publicNamingContexts.remove(dn);
      privateNamingContexts.remove(dn);
    }

    return errors;
  }


  /**
   * Deregisters a base DN with this registry.
   *
   * @param  baseDN to deregister
   * @return list of error messages generated by deregistering the base DN
   *         that should be logged if the changes to this registry are
   *         committed to the server
   * @throws DirectoryException if the base DN could not be deregistered
   */
  public List<Message> deregisterBaseDN(DN baseDN)
         throws DirectoryException
  {
    LinkedList<Message> errors = new LinkedList<Message>();

    ensureNotNull(baseDN);

    // Make sure that the Directory Server actually contains a backend with
    // the specified base DN.
    Backend backend = baseDNs.get(baseDN);
    if (backend == null)
    {
      Message message =
          ERR_DEREGISTER_BASEDN_NOT_REGISTERED.get(String.valueOf(baseDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Check to see if the backend has a parent backend, and whether it has
    // any subordinates with base DNs that are below the base DN to remove.
    Backend             superiorBackend     = backend.getParentBackend();
    LinkedList<Backend> subordinateBackends = new LinkedList<Backend>();
    if (backend.getSubordinateBackends() != null)
    {
      for (Backend b : backend.getSubordinateBackends())
      {
        for (DN dn : b.getBaseDNs())
        {
          if (dn.isDescendantOf(baseDN))
          {
            subordinateBackends.add(b);
            break;
          }
        }
      }
    }


    // See if there are any other base DNs registered within the same backend.
    LinkedList<DN> otherBaseDNs = new LinkedList<DN>();
    for (DN dn : baseDNs.keySet())
    {
      if (dn.equals(baseDN))
      {
        continue;
      }

      Backend b = baseDNs.get(dn);
      if (backend.equals(b))
      {
        otherBaseDNs.add(dn);
      }
    }


    // If we've gotten here, then it's OK to make the changes.

    // Get rid of the references to this base DN in the mapping tree
    // information.
    baseDNs.remove(baseDN);
    publicNamingContexts.remove(baseDN);
    privateNamingContexts.remove(baseDN);

    if (superiorBackend == null)
    {
      // If there were any subordinate backends, then all of their base DNs
      // will now be promoted to naming contexts.
      for (Backend b : subordinateBackends)
      {
        if (!testOnly)
        {
          b.setParentBackend(null);
          backend.removeSubordinateBackend(b);
        }

        for (DN dn : b.getBaseDNs())
        {
          if (b.isPrivateBackend())
          {
            privateNamingContexts.put(dn, b);
          }
          else
          {
            publicNamingContexts.put(dn, b);
          }
        }
      }
    }
    else
    {
      // If there are no other base DNs for the associated backend, then
      // remove this backend as a subordinate of the parent backend.
      if (otherBaseDNs.isEmpty())
      {
        if (!testOnly)
        {
          superiorBackend.removeSubordinateBackend(backend);
        }
      }


      // If there are any subordinate backends, then they need to be made
      // subordinate to the parent backend.  Also, we should log a warning
      // message indicating that there may be inconsistent search results
      // because some of the structural entries will be missing.
      if (! subordinateBackends.isEmpty())
      {
        // Suppress this warning message on server shutdown.
        if (!DirectoryServer.getInstance().isShuttingDown()) {
          Message message = WARN_DEREGISTER_BASEDN_MISSING_HIERARCHY.get(
            String.valueOf(baseDN), backend.getBackendID());
          errors.add(message);
        }

        if (!testOnly)
        {
          for (Backend b : subordinateBackends)
          {
            backend.removeSubordinateBackend(b);
            superiorBackend.addSubordinateBackend(b);
            b.setParentBackend(superiorBackend);
          }
        }
      }
    }
    return errors;
  }


  /**
   * Creates a default instance.
   */
  BaseDnRegistry()
  {
    this(new TreeMap<DN,Backend>(), new TreeMap<DN,Backend>(),
         new TreeMap<DN,Backend>(), false);
  }

  /**
   * Returns a copy of this registry.
   *
   * @return copy of this registry
   */
  BaseDnRegistry copy()
  {
    return new BaseDnRegistry(
            new TreeMap<DN,Backend>(baseDNs),
            new TreeMap<DN,Backend>(publicNamingContexts),
            new TreeMap<DN,Backend>(privateNamingContexts),
            true);
  }


  /**
   * Creates a parameterized instance.
   *
   * @param baseDNs map
   * @param publicNamingContexts map
   * @param privateNamingContexts map
   * @param testOnly indicates whether this registry will be used for testing;
   *        when <code>true</code> this registry will not modify backends
   */
  private BaseDnRegistry(TreeMap<DN, Backend> baseDNs,
                         TreeMap<DN, Backend> publicNamingContexts,
                         TreeMap<DN, Backend> privateNamingContexts,
                         boolean testOnly)
  {
    this.baseDNs = baseDNs;
    this.publicNamingContexts = publicNamingContexts;
    this.privateNamingContexts = privateNamingContexts;
    this.testOnly = testOnly;
  }


  /**
   * Gets the mapping of registered base DNs to their associated backend.
   *
   * @return mapping from base DN to backend
   */
  Map<DN,Backend> getBaseDnMap() {
    return this.baseDNs;
  }


  /**
   * Gets the mapping of registered public naming contexts to their
   * associated backend.
   *
   * @return mapping from naming context to backend
   */
  Map<DN,Backend> getPublicNamingContextsMap() {
    return this.publicNamingContexts;
  }


  /**
   * Gets the mapping of registered private naming contexts to their
   * associated backend.
   *
   * @return mapping from naming context to backend
   */
  Map<DN,Backend> getPrivateNamingContextsMap() {
    return this.privateNamingContexts;
  }




  /**
   * Indicates whether the specified DN is contained in this registry as
   * a naming contexts.
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  {@code true} if the specified DN is a naming context in this
   *          registry, or {@code false} if it is not.
   */
  boolean containsNamingContext(DN dn)
  {
    return (privateNamingContexts.containsKey(dn) ||
            publicNamingContexts.containsKey(dn));
  }


  /**
   * Clear and nullify this registry's internal state.
   */
  void clear() {

    if (baseDNs != null)
    {
      baseDNs.clear();
    }

    if (privateNamingContexts != null)
    {
      privateNamingContexts.clear();
    }

    if (publicNamingContexts != null)
    {
      publicNamingContexts.clear();
    }

  }

}
