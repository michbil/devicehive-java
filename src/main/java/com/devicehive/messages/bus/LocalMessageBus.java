package com.devicehive.messages.bus;

import com.devicehive.configuration.Constants;
import com.devicehive.dao.UserDAO;
import com.devicehive.messages.subscriptions.CommandSubscription;
import com.devicehive.messages.subscriptions.CommandUpdateSubscription;
import com.devicehive.messages.subscriptions.NotificationSubscription;
import com.devicehive.messages.subscriptions.SubscriptionManager;
import com.devicehive.model.DeviceCommand;
import com.devicehive.model.DeviceNotification;
import com.devicehive.model.UserRole;
import com.devicehive.websockets.handlers.ServerResponsesFactory;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javax.ejb.ConcurrencyManagementType.BEAN;


@Singleton
@ConcurrencyManagement(BEAN)
public class LocalMessageBus {
    private static final Logger logger = LoggerFactory.getLogger(LocalMessageBus.class);

    @EJB
    private SubscriptionManager subscriptionManager;

    private ExecutorService primaryProcessingService;

    private ExecutorService handlersService;

    @EJB
    private UserDAO userDAO;

    @PostConstruct
    protected void postConstruct() {
        primaryProcessingService = Executors.newCachedThreadPool();
        handlersService = Executors.newCachedThreadPool();
    }

    @PreDestroy
    protected void preDestroy() {
        primaryProcessingService.shutdown();
        handlersService.shutdown();
    }

    public void submitDeviceCommand(final DeviceCommand deviceCommand) {
        primaryProcessingService.submit(new Runnable() {
            @Override
            public void run() {
                logger.debug("Device command was submitted: {}", deviceCommand.getId());

                JsonObject jsonObject = ServerResponsesFactory.createCommandInsertMessage(deviceCommand);

                Set<CommandSubscription> subs = subscriptionManager.getCommandSubscriptionStorage()
                        .getByDeviceId(deviceCommand.getDevice().getId());
                for (CommandSubscription commandSubscription : subs) {
                    handlersService.submit(commandSubscription.getHandlerCreator().getHandler(jsonObject));
                }
            }
        });
    }

    public void submitDeviceCommandUpdate(final DeviceCommand deviceCommand) {
        primaryProcessingService.submit(new Runnable() {
            @Override
            public void run() {
                logger.debug("Device command update was submitted: {}", deviceCommand.getId());

                JsonObject jsonObject = ServerResponsesFactory.createCommandUpdateMessage(deviceCommand);

                Set<CommandUpdateSubscription> subs = subscriptionManager.getCommandUpdateSubscriptionStorage()
                        .getByCommandId(deviceCommand.getId());
                for (CommandUpdateSubscription commandUpdateSubscription : subs) {
                    handlersService.submit(commandUpdateSubscription.getHandlerCreator().getHandler(jsonObject));
                }
            }
        });
    }

    public void submitDeviceNotification(final DeviceNotification deviceNotification) {
        primaryProcessingService.submit(new Runnable() {
            @Override
            public void run() {
                logger.debug("Device notification was submitted: {}", deviceNotification.getId());

                JsonObject jsonObject = ServerResponsesFactory.createNotificationInsertMessage(deviceNotification);

                Set<NotificationSubscription> subs = subscriptionManager.getNotificationSubscriptionStorage().getByDeviceId(
                        deviceNotification.getDevice().getId());
                for (NotificationSubscription subscription : subs) {
                    if (hasAccess(subscription, deviceNotification)){
                        handlersService.submit(subscription.getHandlerCreator().getHandler(jsonObject));
                    }
                }

                Set<NotificationSubscription> subsForAll = (subscriptionManager.getNotificationSubscriptionStorage()
                        .getByDeviceId(Constants.DEVICE_NOTIFICATION_NULL_ID_SUBSTITUTE));

                for (NotificationSubscription subscription : subsForAll) {
                    if (hasAccess(subscription, deviceNotification)){
                        handlersService.submit(subscription.getHandlerCreator().getHandler(jsonObject));
                    }
                }
            }
        });


    }


    private boolean hasAccess(NotificationSubscription subscription, DeviceNotification deviceNotification) {
        if (subscription.getUser().getRole() == UserRole.ADMIN) {
            return true;
        }
        return userDAO.hasAccessToNetwork(subscription.getUser(), deviceNotification.getDevice().getNetwork());
    }
}
