package com.salesmanager.shop.store.controller.user.facade;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.ws.rs.core.GenericEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.Criteria;
import com.salesmanager.core.model.common.GenericEntityList;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.security.ReadableGroup;
import com.salesmanager.shop.model.security.ReadablePermission;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.ReadableUserList;
import com.salesmanager.shop.populator.user.PersistableUserPopulator;
import com.salesmanager.shop.populator.user.ReadableUserPopulator;
import com.salesmanager.shop.store.api.exception.ConversionRuntimeException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

@Service("userFacade")
public class UserFacadeImpl implements UserFacade {


  @Inject
  private UserService userService;

  @Inject
  private PermissionService permissionService;

  @Inject
  private LanguageService languageService;
  
  @Inject
  private PersistableUserPopulator persistableUserPopulator;

  @Override
  public ReadableUser findByUserName(String userName, Language lang) {
    User user = getByUserName(userName);
    if(user==null) {
      throw new ResourceNotFoundException("User [" + userName + "] not found");
    }
    return convertUserToReadableUser(lang, user);
  }

  private ReadableUser convertUserToReadableUser(Language lang, User user) {
    ReadableUserPopulator populator = new ReadableUserPopulator();
    try {
      return populator.populate(user, new ReadableUser(), user.getMerchantStore(), lang);
    } catch (ConversionException e) {
      throw new ConversionRuntimeException(e);
    }
  }
  
  private User converPersistabletUserToUser(MerchantStore store, Language lang, PersistableUser user) {
    try {
      return persistableUserPopulator.populate(user, new User(), store, lang);
    } catch (ConversionException e) {
      throw new ConversionRuntimeException(e);
    }
  }

  private User getByUserName(String userName) {
    try {
      return userService.getByUserName(userName);
    } catch (ServiceException e) {
      throw new ServiceRuntimeException(e);
    }
  }

/*  @Override
  public ReadableUser findByUserNameWithPermissions(String userName, Language lang) {
    ReadableUser readableUser = findByUserName(userName, lang);

    *//**
     * Add permissions on top of the groups
     *//*
    //List<Integer> groupIds = readableUser.getGroups().stream().map(ReadableGroup::getId)
    //    .map(Long::intValue).collect(Collectors.toList());

    //List<ReadablePermission> permissions = findPermissionsByGroups(groupIds);
    //readableUser.setPermissions(permissions);

    return readableUser;
  }*/

  @Override
  public List<ReadablePermission> findPermissionsByGroups(List<Integer> ids) {
    return getPermissionsByIds(ids).stream()
        .map(permission -> convertPermissionToReadablePermission(permission))
        .collect(Collectors.toList());
  }

  private ReadablePermission convertPermissionToReadablePermission(Permission permission) {
    ReadablePermission readablePermission = new ReadablePermission();
    readablePermission.setId(Long.valueOf(permission.getId()));
    readablePermission.setName(permission.getPermissionName());
    return readablePermission;
  }

  private List<Permission> getPermissionsByIds(List<Integer> ids) {
    try {
      return permissionService.getPermissions(ids);
    } catch (ServiceException e) {
      throw new ServiceRuntimeException(e);
    }
  }

  @Override
  public boolean authorizedStore(String userName, String merchantStoreCode) {

    try {
      ReadableUser readableUser = findByUserName(userName, languageService.defaultLanguage());

      // unless superadmin
      for (ReadableGroup group : readableUser.getGroups()) {
        if (Constants.GROUP_SUPERADMIN.equals(group.getName())) {
          return true;
        }
      }


      boolean authorized = false;
      User user = userService.findByStore(readableUser.getId(), merchantStoreCode);
      if (user != null) {
        authorized = true;
      }

      return authorized;
    } catch (Exception e) {
      throw new ServiceRuntimeException(
          "Cannot authorize user " + userName + " for store " + merchantStoreCode, e.getMessage());
    }
  }



  @Override
  public void authorizedGroup(String userName, List<String> groupName) {

      ReadableUser readableUser = findByUserName(userName, languageService.defaultLanguage());

      // unless superadmin
      for (ReadableGroup group : readableUser.getGroups()) {
        if (groupName.contains(group.getName())) {
          return;
        }
      }

      throw new ServiceRuntimeException(
          "User " + userName + " not authorized");

  }

  @Override
  public String authenticatedUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof AnonymousAuthenticationToken)) {
        String currentUserName = authentication.getName();
        return currentUserName;
    }
    return null;
  }

  @Override
  public void create(PersistableUser user, MerchantStore store) {
    // TODO Auto-generated method stub
    User userModel = converPersistabletUserToUser(store,languageService.defaultLanguage(),user);
    if(CollectionUtils.isEmpty(userModel.getGroups())) {
      throw new ServiceRuntimeException(
          "No valid group groups associated with user " + user.getUserName());
    }
    try {
      userService.saveOrUpdate(userModel);
    } catch (ServiceException e) {
      throw new ServiceRuntimeException(
          "Cannot create user " + user.getUserName() + " for store " + store.getCode(), e);
    }
  }

  @Override
  public ReadableUserList getByCriteria(Language language, String draw, Criteria criteria) {
    try {
      ReadableUserList readableUserList = new ReadableUserList();
      GenericEntityList<User> userList = userService.listByCriteria(criteria);
      for(User user : userList.getList()) {
        ReadableUser readableUser = this.convertUserToReadableUser(language,user);
        readableUserList.getData().add(readableUser);
      }
      readableUserList.setRecordsTotal(userList.getTotalCount());
      readableUserList.setTotalCount(readableUserList.getData().size());
      return readableUserList;
    } catch (ServiceException e) {
      throw new ServiceRuntimeException(
          "Cannot get users by criteria user", e);
    }
  }

}
