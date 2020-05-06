package one.block.androidexampleapp;

import android.os.AsyncTask;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import one.block.arisenjava.error.serializationProvider.SerializationProviderError;
import one.block.arisenjava.error.session.TransactionPrepareError;
import one.block.arisenjava.error.session.TransactionSignAndBroadCastError;
import one.block.arisenjava.implementations.ABIProviderImpl;
import one.block.arisenjava.interfaces.IABIProvider;
import one.block.arisenjava.interfaces.IRPCProvider;
import one.block.arisenjava.interfaces.ISerializationProvider;
import one.block.arisenjava.interfaces.ISignatureProvider;
import one.block.arisenjava.models.rpcProvider.Action;
import one.block.arisenjava.models.rpcProvider.Authorization;
import one.block.arisenjava.models.rpcProvider.Transaction;
import one.block.arisenjava.models.rpcProvider.response.PushTransactionResponse;
import one.block.arisenjava.models.rpcProvider.response.RPCResponseError;
import one.block.arisenjava.session.TransactionProcessor;
import one.block.arisenjava.session.TransactionSession;
import one.block.arisenjavaabirixserializationprovider.AbiRixSerializationProviderImpl;
import one.block.arisenjavarpcprovider.error.ArisenJavaRpcProviderInitializerError;
import one.block.arisenjavarpcprovider.implementations.ArisenJavaRpcProviderImpl;
import one.block.arisensoftkeysignatureprovider.SoftKeySignatureProviderImpl;
import one.block.arisensoftkeysignatureprovider.error.ImportKeyError;

/**
 * This class is an example about the most basic/easy way to use arisen-java to send a transaction.
 * <p>
 * Basic steps:
 * <p>
 *     - Create serialization provider as an instant of {@link AbiRixSerializationProviderImpl} from [arisenjavaandroidabirixserializationprovider] library
 *     <p>
 *     - Create RPC provider as an instant of {@link ArisenJavaRpcProviderImpl} with an input string point to a node backend.
 *     <p>
 *     - Create ABI provider as an instant of {@link ABIProviderImpl} with instants of Rpc provider and serialization provider.
 *     <p>
 *     - Create Signature provider as an instant of {@link SoftKeySignatureProviderImpl} which is not recommended for production because of its simple key management.
 *     <p>
 *         - Import an RSN private key which associate with sender's account which will be used to sign the transaction.
 * <p>
 *     - Create an instant of {@link TransactionSession} which is used for spawning/factory {@link TransactionProcessor}
 * <p>
 *     - Create an instant of {@link TransactionProcessor} from the instant of {@link TransactionSession} above by calling {@link TransactionSession#getTransactionProcessor()} or {@link TransactionSession#getTransactionProcessor(Transaction)} if desire to use a preset {@link Transaction} object.
 * <p>
 *     - Call {@link TransactionProcessor#prepare(List)} with a list of Actions which is desired to be sent to backend. The method will serialize the list of action to list of hex and keep them inside
 * the list of {@link Transaction#getActions()}. The transaction now is ready to be signed and broadcast.
 * <p>
 *     - Call {@link TransactionProcessor#signAndBroadcast()} to sign the transaction inside {@link TransactionProcessor} and broadcast it to backend.
 */
public class TransactionTask extends AsyncTask<String, String, Void> {

    /**
     * Whether the network logs will be enabled for RPC provider
     */
    private static final boolean ENABLE_NETWORK_LOG = true;

    private TransactionTaskCallback callback;

    public TransactionTask(@NonNull TransactionTaskCallback callback) {
        this.callback = callback;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        if (values.length == 1) {
            String message = values[0];
            this.callback.update(message);
        } else if (values.length == 2) {
            boolean isSuccess = Boolean.parseBoolean(values[0]);
            String message = values[1];
            this.callback.finish(isSuccess, message);
        }
    }

    @Override
    protected Void doInBackground(String... params) {
        String nodeUrl = params[0];
        String fromAccount = params[1];
        String toAccount = params[2];
        String privateKey = params[3];
        String amount = params[4];
        String memo = params[5];

        this.publishProgress("Transferring " + amount + " to " + toAccount);

        // Creating serialization provider
        ISerializationProvider serializationProvider;
        try {
            serializationProvider = new AbiRixSerializationProviderImpl();
        } catch (SerializationProviderError serializationProviderError) {
            serializationProviderError.printStackTrace();
            return null;
        }

        // Creating RPC Provider
        IRPCProvider rpcProvider;
        try {
            rpcProvider = new ArisenJavaRpcProviderImpl(nodeUrl, ENABLE_NETWORK_LOG);
        } catch (ArisenJavaRpcProviderInitializerError arisenJavaRpcProviderInitializerError) {
            arisenJavaRpcProviderInitializerError.printStackTrace();
            this.publishProgress(Boolean.toString(false), arisenJavaRpcProviderInitializerError.getMessage());
            return null;
        }

        // Creating ABI provider
        IABIProvider abiProvider = new ABIProviderImpl(rpcProvider, serializationProvider);

        // Creating Signature provider
        ISignatureProvider signatureProvider = new SoftKeySignatureProviderImpl();

        try {
            ((SoftKeySignatureProviderImpl) signatureProvider).importKey(privateKey);
        } catch (ImportKeyError importKeyError) {
            importKeyError.printStackTrace();
            this.publishProgress(Boolean.toString(false), importKeyError.getMessage());
            return null;
        }

        // Creating TransactionProcess
        TransactionSession session = new TransactionSession(serializationProvider, rpcProvider, abiProvider, signatureProvider);
        TransactionProcessor processor = session.getTransactionProcessor();

        // Apply transaction data to Action's data
        String jsonData = "{\n" +
                "\"from\": \"" + fromAccount + "\",\n" +
                "\"to\": \"" + toAccount + "\",\n" +
                "\"quantity\": \"" + amount + "\",\n" +
                "\"memo\" : \"" + memo + "\"\n" +
                "}";

        // Creating action with action's data, arisen.token contract and transfer action.
        Action action = new Action("arisen.token", "transfer", Collections.singletonList(new Authorization(fromAccount, "active")), jsonData);
        try {

            // Prepare transaction with above action. A transaction can be executed with multiple action.
            this.publishProgress("Preparing Transaction...");
            processor.prepare(Collections.singletonList(action));

            // Sign and broadcast the transaction.
            this.publishProgress("Signing and Broadcasting Transaction...");
            PushTransactionResponse response = processor.signAndBroadcast();

            this.publishProgress(Boolean.toString(true), "Finished!  Your transaction id is:  " + response.getTransactionId());
        } catch (TransactionPrepareError transactionPrepareError) {
            // Happens if preparing transaction unsuccessful
            transactionPrepareError.printStackTrace();
            this.publishProgress(Boolean.toString(false), transactionPrepareError.getLocalizedMessage());
        } catch (TransactionSignAndBroadCastError transactionSignAndBroadCastError) {
            // Happens if Sign transaction or broadcast transaction unsuccessful.
            transactionSignAndBroadCastError.printStackTrace();

            // try to get backend error if the error come from backend
            RPCResponseError rpcResponseError = ErrorUtils.getBackendError(transactionSignAndBroadCastError);
            if (rpcResponseError != null) {
                String backendErrorMessage = ErrorUtils.getBackendErrorMessageFromResponse(rpcResponseError);
                this.publishProgress(Boolean.toString(false), backendErrorMessage);
                return null;
            }

            this.publishProgress(Boolean.toString(false), transactionSignAndBroadCastError.getMessage());
        }

        return null;
    }

    public interface TransactionTaskCallback {
        void update(String updateContent);

        void finish(boolean success, String updateContent);
    }
}
