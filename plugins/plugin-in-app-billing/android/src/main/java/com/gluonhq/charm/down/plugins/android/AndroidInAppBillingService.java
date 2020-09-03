/*
 * Copyright (c) 2017, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.charm.down.plugins.android;

import android.content.IntentFilter;
import com.gluonhq.charm.down.Platform;
import com.gluonhq.charm.down.plugins.inappbilling.InAppBillingException;
import com.gluonhq.charm.down.plugins.inappbilling.InAppBillingQueryResult;
import com.gluonhq.charm.down.plugins.inappbilling.InAppBillingQueryResultListener;
import com.gluonhq.charm.down.plugins.InAppBillingService;
import com.gluonhq.charm.down.plugins.inappbilling.Product;
import com.gluonhq.charm.down.plugins.inappbilling.ProductDetails;
import com.gluonhq.charm.down.plugins.inappbilling.ProductOrder;
import com.gluonhq.impl.charm.down.plugins.Constants;
import com.gluonhq.impl.charm.down.plugins.android.util.IabBroadcastReceiver;
import com.gluonhq.impl.charm.down.plugins.android.util.IabException;
import com.gluonhq.impl.charm.down.plugins.android.util.IabHelper;
import com.gluonhq.impl.charm.down.plugins.android.util.IabResult;
import com.gluonhq.impl.charm.down.plugins.android.util.Inventory;
import com.gluonhq.impl.charm.down.plugins.android.util.Purchase;
import com.gluonhq.impl.charm.down.plugins.android.util.SkuDetails;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafxports.android.FXActivity;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AndroidInAppBillingService implements InAppBillingService, IabBroadcastReceiver.IabBroadcastListener {

    private static final Logger LOG = Logger.getLogger(AndroidInAppBillingService.class.getName());

    private static final int REQUEST_CODE = 10011;

    private IabHelper iabHelper;
    private IabBroadcastReceiver iabBroadcastReceiver;
    private IabHelper.QueryInventoryFinishedListener iabQueryInventoryFinishedListener;

    private boolean iabSupported = false;

    private ReadOnlyBooleanWrapper ready = new ReadOnlyBooleanWrapper(false);

    private InAppBillingQueryResultListener queryResultListener;

    private List<Product> registeredProducts = new LinkedList<>();

    public AndroidInAppBillingService() {
        FXActivity.getInstance().setOnActivityResultHandler((requestCode, resultCode, data) -> {
            if (iabHelper != null && iabHelper.handleActivityResult(requestCode, resultCode, data)) {
                LOG.log(Level.INFO, "Successfully handled product order.");
            }
        });
    }

    // TODO: how to unbind service on destroy
    public void onDestroy() throws IabHelper.IabAsyncInProgressException {
        if (iabBroadcastReceiver != null) {
            FXActivity.getInstance().unregisterReceiver(iabBroadcastReceiver);
            iabBroadcastReceiver = null;
        }

        if (iabHelper != null) {
            iabHelper.dispose();
            iabHelper = null;
        }

        iabSupported = false;
    }

    @Override
    public boolean isSupported() {
        return iabSupported;
    }

    @Override
    public void setQueryResultListener(InAppBillingQueryResultListener listener) {
        this.queryResultListener = listener;
    }

    @Override
    public void setRegisteredProducts(List<Product> registeredProducts) {
        this.registeredProducts = registeredProducts;
    }

    @Override
    public void initialize(String androidPublicKey, List<Product> registeredProduct) {
        this.registeredProducts = registeredProduct;

        iabHelper = new IabHelper(FXActivity.getInstance(), androidPublicKey);
        
        if ("true".equals(System.getProperty(Constants.DOWN_DEBUG))) {
            iabHelper.enableDebugLogging(true);
        }

        iabHelper.startSetup(iabResult -> {
            if (iabResult.isFailure()) {
                LOG.log(Level.WARNING, String.format("Problem setting up In-app Billing: %s", iabResult.getMessage()));
                return;
            }

            // Have we been disposed of in the meantime? If so, quit.
            if (iabHelper == null) {
                return;
            }

            iabSupported = iabResult.isSuccess();

            iabQueryInventoryFinishedListener = new AndroidQueryInventoryFinishedListener();

            iabBroadcastReceiver = new IabBroadcastReceiver(AndroidInAppBillingService.this);
            IntentFilter iabBroadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
            FXActivity.getInstance().registerReceiver(iabBroadcastReceiver, iabBroadcastFilter);

            javafx.application.Platform.runLater(() -> ready.set(iabResult.isSuccess()));

            try {
                iabHelper.queryInventoryAsync(iabQueryInventoryFinishedListener);
            } catch (IabHelper.IabAsyncInProgressException e) {
                LOG.log(Level.WARNING, String.format("Query In-App product inventory failed with message: %s", iabResult.getMessage()));
            }
        });
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public ReadOnlyBooleanProperty readyProperty() {
        return ready.getReadOnlyProperty();
    }

    @Override
    public Worker<List<Product>> fetchProductDetails() {
        Task<List<Product>> task = new Task<List<Product>>() {

            @Override
            protected List<Product> call() throws Exception {
                List<Product> detailedProducts = new LinkedList<>();

                if (isReady()) {
                    List<String> productIds = new LinkedList<>();
                    List<String> subscriptionIds = new LinkedList<>();
                    for (Product registeredProduct : registeredProducts) {
                        switch (registeredProduct.getType()) {
                            case CONSUMABLE:
                            case NON_CONSUMABLE:
                                productIds.add(registeredProduct.getId());
                                break;
                            case FREE_SUBSCRIPTION:
                            case RENEWABLE_SUBSCRIPTION:
                            case NON_RENEWABLE_SUBSCRIPTION:
                                subscriptionIds.add(registeredProduct.getId());
                                break;
                        }
                    }

                    try {
                        Inventory inventory = iabHelper.queryInventory(true, productIds, subscriptionIds);
                        for (Product product : registeredProducts) {
                            if (inventory.hasDetails(product.getId()) || inventory.hasPurchase(product.getId())) {
                                Product detailedProduct = new Product(product.getId(), product.getType());
                                fillProductDetails(detailedProduct, inventory);

                                detailedProducts.add(detailedProduct);
                            }
                        }
                    } catch (IabException e) {
                        LOG.log(Level.WARNING, "Failed to query product inventory.", e);
                        throw new InAppBillingException(e.getMessage());
                    } catch (IllegalStateException e) {
                        LOG.log(Level.WARNING, e.getMessage(), e);
                        throw new InAppBillingException(e.getMessage());
                    }
                }

                return detailedProducts;
            }
        };

        Thread thread = new Thread(task);
        thread.start();
        return task;
    }

    @Override
    public Worker<ProductOrder> order(Product product) {
        Task<ProductOrder> task = new Task<ProductOrder>() {

            @Override
            protected ProductOrder call() throws Exception {
                if (isReady()) {
                    ObjectProperty<IabResult> futureResult = new SimpleObjectProperty<>();
                    ObjectProperty<ProductOrder> future = new SimpleObjectProperty<>();
                    CountDownLatch latch = new CountDownLatch(1);

                    try {
                        String payload = UUID.randomUUID().toString();
                        iabHelper.launchPurchaseFlow(FXActivity.getInstance(), product.getId(),
                                REQUEST_CODE, (IabResult result, Purchase purchase) -> {
                                    try {
                                        futureResult.set(result);
                                        
                                        if (result.isFailure()) {
                                            LOG.log(Level.WARNING, String.format("Failed to process order: %s", result));
                                            return;
                                        }
                                        
                                        if (!payload.equals(purchase.getDeveloperPayload())) {
                                            futureResult.set(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "Authenticity verification failed."));
                                            return;
                                        }
                                        
                                        ProductOrder productOrder = new ProductOrder();
                                        productOrder.setProduct(product);
                                        productOrder.setPlatform(Platform.ANDROID);
                                        productOrder.setFields(getProductOrderFields(purchase));
                                        future.set(productOrder);
                                    } finally {
                                        latch.countDown();
                                    }
                        }, payload);

                    } catch (IabHelper.IabAsyncInProgressException e) {
                        throw new InAppBillingException("Another product order is already in progress.");
                    }

                    if (latch.await(15, TimeUnit.MINUTES)) {
                        ProductOrder productOrder = future.get();
                        if (productOrder == null) {
                            throw new InAppBillingException(futureResult.get().getMessage());
                        }
                        return productOrder;
                    } else {
                        throw new InAppBillingException("Product order operation timed out.");
                    }
                }
                return null;
            }
        };

        Thread thread = new Thread(task);
        thread.start();
        return task;
    }

    @Override
    public Worker<Product> finish(ProductOrder productOrder) {
        Task<Product> task = new Task<Product>() {

            @Override
            protected Product call() throws Exception {
                if (isReady()) {
                    ObjectProperty<Product> future = new SimpleObjectProperty<>();
                    CountDownLatch latch = new CountDownLatch(1);

                    iabHelper.consumeAsync((Purchase) productOrder.getFields().get("purchase"), 
                            (Purchase purchase, IabResult result) -> {
                        try {
                            if (result.isSuccess()) {
                                Product product = productOrder.getProduct();
                                product.getDetails().setState(ProductDetails.State.FINISHED);
                                future.set(product);
                            } else {
                                LOG.log(Level.WARNING, String.format("Failed to finish order: %s", result.getMessage()));
                            }
                        } finally {
                            latch.countDown();
                        }
                    });

                    latch.await(15, TimeUnit.SECONDS);
                    return future.get();
                } else {
                    return null;
                }
            }
        };

        Thread thread = new Thread(task);
        thread.start();
        return task;
    }

    @Override
    public void receivedBroadcast() {
        if (iabHelper != null) {
            try {
                iabHelper.queryInventoryAsync(iabQueryInventoryFinishedListener);
            } catch (IabHelper.IabAsyncInProgressException e) {
                LOG.log(Level.WARNING, "Failed to query product inventory.", e);
            }
        }
    }

    private void fillProductDetails(Product product, Inventory inventory) {
        ProductDetails details = new ProductDetails();
        product.setDetails(details);

        if (inventory.hasDetails(product.getId())) {
            SkuDetails skuDetails = inventory.getSkuDetails(product.getId());

            details.setTitle(skuDetails.getTitle());
            details.setDescription(skuDetails.getDescription());
            details.setPrice(skuDetails.getPrice());
            details.setCurrency(skuDetails.getPriceCurrencyCode());
        }

        if (inventory.hasPurchase(product.getId())) {
            Purchase purchase = inventory.getPurchase(product.getId());

            if (purchase.getPurchaseState() == 0) { // state 0 == purchased
                details.setState(ProductDetails.State.APPROVED);
            }
        }
    }

    private Map<String, Object> getProductOrderFields(Purchase purchase) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("purchase", purchase);
        fields.put("orderId", purchase.getOrderId());
        fields.put("developerPayload", purchase.getDeveloperPayload());
        fields.put("purchaseTime", purchase.getPurchaseTime());
        fields.put("signature", purchase.getSignature());
        fields.put("token", purchase.getToken());
        return fields;
    }

    public class AndroidQueryInventoryFinishedListener implements IabHelper.QueryInventoryFinishedListener {

        @Override
        public void onQueryInventoryFinished(IabResult iabResult, Inventory inventory) {
            if (iabResult.isSuccess()) {
                if (queryResultListener != null) {
                    InAppBillingQueryResult result = new InAppBillingQueryResult();

                    for (Product product : registeredProducts) {
                        if (inventory.hasPurchase(product.getId())) {
                            ProductOrder productOrder = new ProductOrder();

                            Product detailedProduct = new Product(product.getId(), product.getType());
                            fillProductDetails(detailedProduct, inventory);

                            productOrder.setProduct(detailedProduct);
                            productOrder.setPlatform(Platform.ANDROID);
                            productOrder.setFields(getProductOrderFields(inventory.getPurchase(product.getId())));

                            result.getProductOrders().add(productOrder);
                        }
                    }

                    javafx.application.Platform.runLater(() -> queryResultListener.onQueryResultReceived(result));
                }
            } else {
                LOG.log(Level.WARNING, String.format("Query In-App product inventory failed with message: %s", iabResult.getMessage()));
            }
        }
    }
}
