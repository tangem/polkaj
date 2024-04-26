package io.emeraldpay.polkaj.apihttp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.emeraldpay.polkaj.api.RpcCall;
import io.emeraldpay.polkaj.api.RpcCallAdapter;
import io.emeraldpay.polkaj.api.RpcCoder;
import io.emeraldpay.polkaj.api.RpcException;
import io.emeraldpay.polkaj.apihttp.ext.CompletableFutureKt;
import io.emeraldpay.polkaj.json.jackson.PolkadotModule;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class JavaRetrofitAdapter implements RpcCallAdapter {

    private final PolkadotRetrofitApi service;
    private final RpcCoder rpcCoder;

    private Runnable onCloseListener;
    private boolean isClosed = false;

    public JavaRetrofitAdapter(
            String baseUrl,
            OkHttpClient okHttpClient,
            RpcCoder rpcCoder
    ) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new PolkadotModule());
        JacksonConverterFactory converterFactory = JacksonConverterFactory.create(mapper);

        this.service = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(converterFactory)
                .client(okHttpClient)
                .build()
                .create(PolkadotRetrofitApi.class);

        this.rpcCoder = rpcCoder;
    }

    public void setOnCloseListener(Runnable onCloseListener) {
        this.onCloseListener = onCloseListener;
    }

    @Override
    public <T> CompletableFuture<T> produceRpcFuture(RpcCall<T> call) {
        if (isClosed) {
            return CompletableFutureKt.failedFuture(new IllegalStateException("Client is already closed"));
        }

        int id = rpcCoder.nextId();
        JavaType type = call.getResultType(rpcCoder.getObjectMapper().getTypeFactory());

        try {
            MediaType mediaType = MediaType.get("application/json; charset=utf-8");
            byte[] encodedRpcRequest = rpcCoder.encode(id, call);
            RequestBody requestBody = RequestBody.create(mediaType, encodedRpcRequest);
            Response<ResponseBody> response = service.post(requestBody).execute();
            if (response.code() != 200) {
                RpcException exception = new RpcException(-32000, "Server returned error status: " + response.code());
                return CompletableFutureKt.failedFuture(exception);
            }
            if (response.body() == null) {
                RpcException exception = new RpcException(-32000, "Server returned empty body");
                return CompletableFutureKt.failedFuture(exception);
            }

            String jsonBody = response.body().string();
            Object result = rpcCoder.decode(id, jsonBody, type);
            return CompletableFuture.completedFuture(((T) result));
        } catch (JsonProcessingException e) {
            RpcException exception = new RpcException(-32600, "Unable to encode request as JSON: " + e.getMessage(), e);
            return CompletableFutureKt.failedFuture(exception);
        } catch (CompletionException ex) {
            RpcException exception = new RpcException(-32600, "Unable to decode response from JSON: " + ex.getMessage(), ex);
            return CompletableFutureKt.failedFuture(exception);
        } catch (Exception e) {
            return CompletableFutureKt.failedFuture(e);
        }
    }

    @Override
    public void close() throws Exception {
        if (isClosed) return;

        isClosed = true;
        if (onCloseListener != null) onCloseListener.run();
    }
}