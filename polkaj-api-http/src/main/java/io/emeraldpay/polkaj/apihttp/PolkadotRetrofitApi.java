package io.emeraldpay.polkaj.apihttp;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

interface PolkadotRetrofitApi {
    @POST(".")
    Call<ResponseBody> post(@Body RequestBody var1);
}
