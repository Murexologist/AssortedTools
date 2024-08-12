package com.murexologist;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

import murex.shared.cryptography.CertificateBase64;

public class LicenseToCrypt {

	static final SecretKeySpec DEFAULT_KEY = new SecretKeySpec(new byte[]{116, -122, 37, 62, -104, -65, -53, 122, -73, 32, -26, 99, 114, -58, 24, -119}, "AES");
	static final Cipher DEFAULT_CIPHER;
	static final Signature DEFAULT_SIGNATURE;
	static {
		try {
			DEFAULT_CIPHER = Cipher.getInstance("AES/CBC/PKCS5Padding");
			DEFAULT_SIGNATURE = Signature.getInstance("SHA1withDSA");
		} catch ( final NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(final String[] args) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException {
		final String[] myArgs;
		if(args == null || args.length!= 2){
			myArgs = new String[2];
			final Scanner scanner = new Scanner(System.in);
			System.out.print("FS Host: ");
			myArgs[0] = scanner.next();
			System.out.print("FS Port: ");
			myArgs[1] = scanner.next();
		}else{
			myArgs = args;
		}
		final URL fsUrl = new URL("http", myArgs[0],Integer.parseInt(myArgs[1]),"/");
		final SecretKeySpec secretKeySpec = getSecretKeySpec(fsUrl);
		final byte[] encryptedCustomerRightsBytes = getEncryptedCustomerRightsBytes(fsUrl);
		System.err.println(decryptCustomerRights(secretKeySpec, encryptedCustomerRightsBytes));
	}

	private static String decryptCustomerRights(final SecretKeySpec secretKeySpec, final byte[] encryptedCustomerRightsBytes) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
		final IvParameterSpec algorithmParameterSpec = new IvParameterSpec(Arrays.copyOfRange(encryptedCustomerRightsBytes, 0, 16));
		DEFAULT_CIPHER.init(Cipher.DECRYPT_MODE, secretKeySpec, algorithmParameterSpec);
		final byte[] decryptedCustomerRights = DEFAULT_CIPHER.doFinal(encryptedCustomerRightsBytes, 16, encryptedCustomerRightsBytes.length - 16);
		return new String(decryptedCustomerRights);
	}

	private static byte[] getEncryptedCustomerRightsBytes(final URL fsUrl) throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException, InvalidKeyException {
		try(final InputStream is = readFromFs(fsUrl.toString()+"license/customerrights.mxres")) {
			final byte[] encryptedCustomerRightsAndSignature = is.readAllBytes();
			final int signatureLength = encryptedCustomerRightsAndSignature[0];
			final byte[] encryptedCustomerRightsBytes = new byte[encryptedCustomerRightsAndSignature.length - signatureLength - 1];
			System.arraycopy(encryptedCustomerRightsAndSignature, 1, encryptedCustomerRightsBytes, 0, encryptedCustomerRightsBytes.length);
			final byte[] signatureBytes = new byte[signatureLength];
			System.arraycopy(encryptedCustomerRightsAndSignature, 1 + encryptedCustomerRightsBytes.length, signatureBytes, 0, signatureLength);
			verifySignatureOrThrow(fsUrl, encryptedCustomerRightsBytes, signatureBytes);
			return encryptedCustomerRightsBytes;
		}
	}

	private static SecretKeySpec getSecretKeySpec(final URL fsUrl) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
		final DataInputStream disKey = new DataInputStream((new URL("jar:" + fsUrl.toString() + "license/common-settings.jar!/murex/license/key.txt"))
															   .openConnection()
															   .getInputStream());
		final byte[] cypherBytes = new byte[disKey.readInt()];
		disKey.read(cypherBytes);
		final byte[] ciphertext = Base64.getMimeDecoder().decode(new String(cypherBytes));
		final IvParameterSpec algorithmParameterSpec1 = new IvParameterSpec(Arrays.copyOfRange(ciphertext, 0, 16));
		DEFAULT_CIPHER.init(2, DEFAULT_KEY, algorithmParameterSpec1);
		final byte[] decryptedBytes = DEFAULT_CIPHER.doFinal(ciphertext, 16, ciphertext.length - 16);
		//Hacky ObjectInputStream workaround, because we don't have the bytecode of this particular Murex class, but we kinda expect the structure :
		final String[] arr =new String(decryptedBytes).split("t\\u0000.");
		final byte[] key = (new BigInteger(arr[arr.length-1], 16)).toByteArray();
		return new SecretKeySpec(key, arr[arr.length - 3]);
	}

	private static void verifySignatureOrThrow(final URL fsUrl, final byte[] encryptedCustomerRights, final byte[] signatureBytes ) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		final PublicKey publicKey = getPublicKey(fsUrl);
		DEFAULT_SIGNATURE.initVerify(publicKey);
		DEFAULT_SIGNATURE.update(encryptedCustomerRights);
		if (!DEFAULT_SIGNATURE.verify(signatureBytes)) {
			throw new RuntimeException("Signature does not match document !");
		}
	}

	private static PublicKey getPublicKey(final URL fsUrl) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
		final DataInputStream disPublicKey = new DataInputStream((new URL("jar:" + fsUrl.toString() + "license/common-settings.jar!/murex/license/publicKey.txt"))
																	 .openConnection()
																	 .getInputStream());
		int len = disPublicKey.readInt();
		byte[] buffer = new byte[len];
		disPublicKey.read(buffer, 0, len);
		final String algorithm = new String(buffer);
		len = disPublicKey.readInt();
		buffer = new byte[len];
		disPublicKey.read(buffer, 0, len);
		final byte[] decoded = CertificateBase64.decode(new String(buffer));
		return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(decoded));
	}

	public static InputStream readFromFs(final String path) throws IOException {
		final URLConnection connection = (new URL(path)).openConnection();
		connection.setRequestProperty("MUREX-EXTENSION", "murex");
		return connection.getInputStream();
	}
}
