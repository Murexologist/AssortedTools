--https://docs.oracle.com/database/121/JJDBC/ssid.htm#JJDBC28250
/*
 * v1.2
 * This script create the following SQL functions to deal with Blobs, Zip archives and Xml:
 * UNZIPBLOB : Takes a Blob representing a ZIP file, and returns the data from the first file in the archive
 * ZIPBLOB : Returns Blob which is a Zip archive with on entry named XMLZIP that is the data passed as parameter.
 * ZIPCLOB : Same as above, but for a Clob parameter
 * BLOBTOCLOB : Converts a Blob into a Clob (current Globalization Support character set is used)
 * MXBLOBTOCLOB : Converts a Blob to a Clob, after unzipping its content depending on the input flag value.
 * GETJAVASYSTEMPROPERTY : troubleshooting utility method returning any property from Java System.getProperty().
 * XMLISWELLFORMED : Test is a Clob is a valid XML. Returns 1 is true, 0 otherwise.
 * SAFEXMLTYPE : Returns an XmlType from a Clob if it is a valid Xml document, null otherwise.
 *
 * In the Murex DB some data is stored as Zipped blobs.
 * These method are useful to integrate the data in SQL queries.
 * This is especially useful for XML content, as once extracted, it can be processed further using Oracle XML DB functions.
 * For more info, see: https://docs.oracle.com/en/database/oracle/oracle-database/12.2/adxdb/xml-db-developers-guide.pdf
 *
 */

ALTER SESSION SET PLSQL_WARNINGS = 'Enable:All';
/

ALTER SESSION SET PLSQL_OPTIMIZE_LEVEL = 3;
/

/* Oracle 11g version of the Java Source DB object. */
/* On Oracle 11g, the server-side internal driver (kprb) version */
/* Blob support is implemented through Oracle Object Types (oracle.sql.BLOB). */
--	CREATE OR REPLACE AND COMPILE JAVA SOURCE NAMED MxBlobUtil AUTHID CURRENT_USER AS
--	package com.murexologist;
--	
--	import java.io.Closeable;
--	import java.io.IOException;
--	import java.io.InputStream;
--	import java.io.OutputStream;
--	import java.sql.Blob;
--	import java.sql.SQLException;
--	import java.util.zip.ZipInputStream;
--	import java.util.zip.ZipEntry;
--	import java.util.zip.ZipOutputStream;
--	import oracle.sql.BLOB;
--	import oracle.sql.CLOB;
--	import oracle.jdbc.OracleDriver;
--	
--	public class MxBlobUtil {
--	
--		public static String getJavaSystemProperty(String propertyName){
--			return System.getProperty(propertyName);
--		}
--	
--		public static BLOB unZipBlob(BLOB inBlob) throws SQLException, IOException {
--			if (inBlob == null || inBlob.length() == 0)
--				return null;
--			/* The connection object acquired here has to be NOT closed*/
--			BLOB outBlob = BLOB.createTemporary((new OracleDriver()).defaultConnection(),true,BLOB.DURATION_CALL);
--			OutputStream  out = null;
--			InputStream is = null;
--			ZipInputStream zis = null;
--			try {
--				out = outBlob.setBinaryStream(1L);
--				is = inBlob.getBinaryStream();
--				zis = new ZipInputStream(is);
--				zis.getNextEntry();
--				byte[] buffer = new byte[1024];
--				int read = 0;
--				while ((read = zis.read(buffer, 0, buffer.length)) != -1) {
--					out.write(buffer, 0, read);
--				}		
--				out.flush();
--			}finally {
--				closeQuietly(out);
--				closeQuietly(zis);
--				closeQuietly(is);
--			}
--			return outBlob;
--		}
--	
--		public static BLOB zipBlob(BLOB inBlob) throws SQLException, IOException {
--			if (inBlob == null || inBlob.length() == 0)
--				return null;
--			return writeZipAndClose(inBlob.getBinaryStream());
--		}
--	
--		public static BLOB zipClob(CLOB inClob) throws SQLException, IOException {
--			if (inClob == null || inClob.length() == 0)
--				return null;
--			return writeZipAndClose( inClob.getAsciiStream());
--		}
--	
--		private static BLOB writeZipAndClose(InputStream is) throws IOException, SQLException  {
--			/* The connection object acquired here has to be NOT closed*/
--			BLOB outBlob = BLOB.createTemporary((new OracleDriver()).defaultConnection(),true,BLOB.DURATION_CALL);;
--			ZipOutputStream zos = null;
--			OutputStream  out = null;
--			try {
--				 out = outBlob.setBinaryStream(1L);
--				zos = new ZipOutputStream(out);
--				zos.putNextEntry(new ZipEntry("XMLZIP"));
--				byte[] buffer = new byte[1024];
--				int read = 0;
--				while ((read = is.read(buffer, 0, buffer.length)) != -1) {
--					zos.write(buffer, 0, read);
--				}
--			}finally {
--				closeQuietly(zos);
--				closeQuietly(out);
--				closeQuietly(is);
--			}
--			return outBlob;
--		}
--	
--		private static void closeQuietly(Closeable  c) {
--			if(c != null) {
--				try {
--					c.close();
--				}catch(Exception e) {
--					/*Nothing to do.*/
--				}
--			}
--		}
--	};
--	/
--
--CREATE OR REPLACE FUNCTION UNZIPBLOB(inBlob IN BLOB) RETURN BLOB AUTHID CURRENT_USER AS
--LANGUAGE JAVA NAME 'com.murexologist.MxBlobUtil.unZipBlob(oracle.sql.BLOB) return oracle.sql.BLOB';
--/
--
--CREATE OR REPLACE FUNCTION ZIPBLOB(inBlob IN BLOB) RETURN BLOB AUTHID CURRENT_USER AS
--LANGUAGE JAVA NAME 'com.murexologist.MxBlobUtil.zipBlob(oracle.sql.BLOB) return oracle.sql.BLOB';
--/
--
--CREATE OR REPLACE FUNCTION ZIPCLOB(inClob IN CLOB) RETURN BLOB AUTHID CURRENT_USER AS
--LANGUAGE JAVA NAME 'com.murexologist.MxBlobUtil.zipClob(oracle.sql.CLOB) return oracle.sql.BLOB';
--/


/* Oracle 12g Version of the Java Source DB object.*/
/* On Oracle 12c, the server-side internal driver (kprb) supports JDBC3 specification*/
/* Standard JDBC classes can be used for Blob support.*/
/* The Oracle extension class (oracle.sql.BLOB) has been deprecated.*/
/* See https://docs.oracle.com/database/121/JJDBC/ssid.htm#JJDBC28252 */
CREATE OR REPLACE AND COMPILE JAVA SOURCE NAMED MxBlobUtil AUTHID CURRENT_USER AS
package com.murexologist;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import oracle.jdbc.OracleDriver;

public class MxBlobUtil {

	public static String getJavaSystemProperty(String propertyName){
		return System.getProperty(propertyName);
	}

	public static Blob unZipBlob(Blob inBlob) throws SQLException, IOException {
		if (inBlob == null || inBlob.length() == 0)
			return null;
		/* The connection object acquired here has to be NOT closed*/
		Blob outBlob = (new OracleDriver()).defaultConnection().createBlob();
		OutputStream  out = null;
		InputStream is = null;
		ZipInputStream zis = null;
		try {
			out = outBlob.setBinaryStream(1L);
			is = inBlob.getBinaryStream();
			zis = new ZipInputStream(is);
			zis.getNextEntry();
			byte[] buffer = new byte[1024];
			int read = 0;
			while ((read = zis.read(buffer, 0, buffer.length)) != -1) {
				out.write(buffer, 0, read);
			}
			out.flush();
		}finally {
			closeQuietly(out);
			closeQuietly(zis);
			closeQuietly(is);
		}
		return outBlob;
	}

	public static Blob zipBlob(Blob inBlob) throws SQLException, IOException {
		if (inBlob == null || inBlob.length() == 0)
			return null;
		return writeZipAndClose(inBlob.getBinaryStream());
	}

	public static Blob zipClob(Clob inClob) throws SQLException, IOException {
		if (inClob == null || inClob.length() == 0)
			return null;
		return writeZipAndClose( inClob.getAsciiStream());
	}


	private static Blob writeZipAndClose(InputStream is) throws IOException, SQLException  {
		/* The connection object acquired here has to be NOT closed*/
		Blob outBlob = (new OracleDriver()).defaultConnection().createBlob();
		ZipOutputStream zos = null;
		OutputStream  out = null;
		try {
			 out = outBlob.setBinaryStream(1L);
			zos = new ZipOutputStream(out);
			zos.putNextEntry(new ZipEntry("XMLZIP"));
			byte[] buffer = new byte[1024];
			int read = 0;
			while ((read = is.read(buffer, 0, buffer.length)) != -1) {
				zos.write(buffer, 0, read);
			}
		}finally {
			closeQuietly(zos);
			closeQuietly(out);
			closeQuietly(is);
		}
		return outBlob;
	}
	
	private static void closeQuietly(Closeable  c) {
		if(c != null) {
			try {
				c.close();
			}catch(Exception e) {
				/*Nothing to do.*/
			}
		}
	}
};
/

CREATE OR REPLACE FUNCTION UNZIPBLOB(blob_in IN BLOB) RETURN BLOB AUTHID CURRENT_USER AS
LANGUAGE JAVA NAME 'com.murexologist.MxBlobUtil.unZipBlob(java.sql.Blob) return java.sql.Blob';
/

CREATE OR REPLACE FUNCTION ZIPBLOB(blob_in IN BLOB) RETURN BLOB AUTHID CURRENT_USER AS
LANGUAGE JAVA NAME 'com.murexologist.MxBlobUtil.zipBlob(java.sql.Blob) return java.sql.Blob';
/

CREATE OR REPLACE FUNCTION ZIPCLOB(blob_in IN CLOB) RETURN BLOB AUTHID CURRENT_USER AS
LANGUAGE JAVA NAME 'com.murexologist.MxBlobUtil.zipClob(java.sql.Clob) return java.sql.Blob';
/

CREATE OR REPLACE FUNCTION GETJAVASYSTEMPROPERTY(propertyName IN VARCHAR2) RETURN VARCHAR2 AUTHID CURRENT_USER AS
LANGUAGE JAVA NAME 'com.murexologist.MxBlobUtil.getJavaSystemProperty(java.lang.String) return java.lang.String';
/

GRANT EXECUTE ON UNZIPBLOB TO MUREX;
/

GRANT EXECUTE ON ZIPBLOB TO MUREX;
/

GRANT EXECUTE ON ZIPCLOB TO MUREX;
/

GRANT EXECUTE ON GETJAVASYSTEMPROPERTY TO MUREX;
/

CREATE OR REPLACE FUNCTION BLOBTOCLOB (blob_in IN BLOB)
RETURN CLOB AUTHID CURRENT_USER
AS
	v_clob    CLOB;
	v_varchar VARCHAR2(32767);
	v_start   PLS_INTEGER := 1;
	v_buffer  PLS_INTEGER := 32767;
BEGIN
	IF blob_in IS NULL THEN
		RETURN NULL;
	 END IF;
	DBMS_LOB.CREATETEMPORARY(v_clob, TRUE);
	FOR i IN 1..CEIL(DBMS_LOB.GETLENGTH(blob_in) / v_buffer)
	LOOP
		v_varchar := UTL_RAW.CAST_TO_VARCHAR2(DBMS_LOB.SUBSTR(blob_in, v_buffer, v_start));
		DBMS_LOB.WRITEAPPEND(v_clob, LENGTH(v_varchar), v_varchar);
		v_start := v_start + v_buffer;
	END LOOP;
	RETURN v_clob;
END BLOBTOCLOB;
/

GRANT EXECUTE ON BLOBTOCLOB TO MUREX;
/

CREATE OR REPLACE FUNCTION MXBLOBTOCLOB (blob_in IN BLOB, XML_LZ IN NUMBER)
RETURN CLOB AUTHID CURRENT_USER
AS
	v_clob    CLOB;
BEGIN
	IF XML_LZ = 0 THEN
		v_clob := BLOBTOCLOB(blob_in);
	ELSE
		v_clob := BLOBTOCLOB(UNZIPBLOB(blob_in));
	END IF;
	RETURN v_clob;
END MXBLOBTOCLOB;
/

GRANT EXECUTE ON MXBLOBTOCLOB TO MUREX;
/

create or replace FUNCTION SAFEXMLTYPE (p_xml IN CLOB) RETURN XMLTYPE AUTHID CURRENT_USER
IS
  l_XMLTYPE XMLTYPE;
BEGIN
    SELECT XMLParse(DOCUMENT (REGEXP_REPLACE(p_xml, '<!DOCTYPE[^<]*>', '')) ) COLLECT INTO l_XMLTYPE FROM DUAL;
    RETURN l_XMLTYPE;
EXCEPTION
    WHEN OTHERS THEN RETURN NULL;
END SAFEXMLTYPE;
/

GRANT EXECUTE ON SAFEXMLTYPE TO MUREX;
/

CREATE OR REPLACE FUNCTION MXBLOBTOXMLTYPE (blob_in IN BLOB, XML_LZ IN NUMBER)
RETURN XMLTYPE AUTHID CURRENT_USER
AS
	v_clob    XMLTYPE;
BEGIN
	IF XML_LZ = 0 THEN
		v_clob := SAFEXMLTYPE(BLOBTOCLOB(blob_in));
	ELSE
		v_clob := SAFEXMLTYPE(BLOBTOCLOB(UNZIPBLOB(blob_in)));
	END IF;
	RETURN v_clob;
END MXBLOBTOXMLTYPE;
/


