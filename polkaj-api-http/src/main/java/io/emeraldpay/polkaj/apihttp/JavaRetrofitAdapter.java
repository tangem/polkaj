package io.emeraldpay.polkaj.apihttp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.emeraldpay.polkaj.api.RpcCall;
import io.emeraldpay.polkaj.api.RpcCallAdapter;
import io.emeraldpay.polkaj.api.RpcCoder;
import io.emeraldpay.polkaj.api.RpcException;
import io.emeraldpay.polkaj.json.jackson.PolkadotModule;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class JavaRetrofitAdapter implements RpcCallAdapter {
    private final PolkadotRetrofitApi service;
    private final RpcCoder rpcCoder;
    private Runnable onCloseListener;
    private boolean isClosed = false;

    public JavaRetrofitAdapter(String baseUrl, OkHttpClient okHttpClient, RpcCoder rpcCoder) {
        ObjectMapper mapper = (new ObjectMapper()).registerModule(new PolkadotModule());
        JacksonConverterFactory converterFactory = JacksonConverterFactory.create(mapper);
        this.service = (new Retrofit.Builder()).baseUrl(baseUrl).addConverterFactory(converterFactory).client(okHttpClient).build().create(PolkadotRetrofitApi.class);
        this.rpcCoder = rpcCoder;
    }

    public void setOnCloseListener(Runnable onCloseListener) {
        this.onCloseListener = onCloseListener;
    }

    public <T> CompletableFuture<T> produceRpcFuture(RpcCall<T> call) {
        if (this.isClosed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is already closed"));
        } else {
            int id = this.rpcCoder.nextId();
            JavaType type = call.getResultType(this.rpcCoder.getObjectMapper().getTypeFactory());

            RpcException exception;
            try {
                MediaType mediaType = MediaType.get("application/json; charset=utf-8");
                byte[] encodedRpcRequest = this.rpcCoder.encode(id, call);
                RequestBody requestBody = RequestBody.create(mediaType, encodedRpcRequest);
                Response<ResponseBody> response = this.service.post(requestBody).execute();
                if (response.code() != 200) {
                    exception = new RpcException(-32000, "Server returned error status: " + response.code());
                    return CompletableFuture.failedFuture(exception);
                } else if (response.body() == null) {
                    exception = new RpcException(-32000, "Server returned empty body");
                    return CompletableFuture.failedFuture(exception);
                } else {
                    String jsonBody = response.body().string();
                    T result = this.rpcCoder.decode(id, jsonBody, type);
                    return CompletableFuture.completedFuture(result);
                }
            } catch (JsonProcessingException var10) {
                exception = new RpcException(-32600, "Unable to encode request as JSON: " + var10.getMessage(), var10);
                return CompletableFuture.failedFuture(exception);
            } catch (CompletionException var11) {
                exception = new RpcException(-32600, "Unable to decode response from JSON: " + var11.getMessage(), var11);
                return CompletableFuture.failedFuture(exception);
            } catch (Exception var12) {
                return CompletableFuture.failedFuture(var12);
            }
        }
    }

    public void close() throws Exception {
        if (!this.isClosed) {
            this.isClosed = true;
            if (this.onCloseListener != null) {
                this.onCloseListener.run();
            }

        }
    }
}
