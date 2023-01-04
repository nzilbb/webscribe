//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of webscribe.
//
//    This is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
//    (at your option) any later version.
//
//    This software is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this software; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
package nzilbb.webscribe;

import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

/**
 * Command line utitiliy for upgrading an installed webapp from the .war file.
 * <p> Replacing a .war file to upgrade the web-application causes all previously
 * installed files to be deleted, including files that were installed after the web-app
 * installation. This means that large ASR models that were downloaded as required are
 * deleted and must be downloaded again.
 * <p> To avoid this, this command-line utility allows in-situ upgrade of the
 * web-application without destruction of any such ad-hoc files, and also any
 * customizations made to the web.xml file.
 * <p> To upgrade this way, instead of replacing the .war file, place the new .war file in
 * some other location, and use a command-line shell to change directory to the web-app
 * directory. Then invoke this utility. e.g.
 * <p><tt>
 * cd /var/lib/tomcat/webapps/webscribe <br>
 * java -jar ~/webscribe.war
 * </tt>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Upgrade {
   public static void main(String argv[]) {
     URL url = Upgrade.class.getResource(Upgrade.class.getSimpleName() + ".class");
     String sUrl = url.toString();
     if (!sUrl.startsWith("jar:")) {
       System.err.println("Upgrade must be run from within a war archive file.");
       System.exit(1);
       return;
     }

     int iUriStart = 4;
     int iUriEnd = sUrl.indexOf("!");
     String sFileUri = sUrl.substring(iUriStart, iUriEnd);
     try {
       File fJar = new File(new URI(sFileUri));
       System.out.println("Unpacking from " + fJar.getPath());
       JarFile jfJar = new JarFile(fJar);
       
       File root = new File(".").getCanonicalFile(); // current directory
       File oldPomPropertiesFile = new File(
         new File(new File(new File(new File(root, "META-INF"),
                                    "maven"), "nzilbb"), "nzilbb.webscribe"), "pom.properties");
       if (!oldPomPropertiesFile.exists()) {
         System.err.println("Can't find: " + oldPomPropertiesFile.getPath());
         System.err.println("Upgrade must be run in directory where current version is installed.");
         System.exit(2);
         return;
       }
       
       // check for version files
       try {
         Properties oldPomProperties = new Properties();
         InputStream inStream = new FileInputStream(oldPomPropertiesFile);
         oldPomProperties.load(inStream);
         inStream.close();
         
         Properties newPomProperties = new Properties();
         inStream = jfJar.getInputStream(
           jfJar.getJarEntry("META-INF/maven/nzilbb/nzilbb.webscribe/pom.properties"));
         newPomProperties.load(inStream);
         inStream.close();
         
         System.out.println(
           "Upgrading from " + oldPomProperties.getProperty("version")
           + " to " + newPomProperties.getProperty("version"));
         
       } catch(Throwable exception) {
         System.err.println("Error checking old/new version: " + exception);
         System.exit(3);
         return;
       }
       
       // unpack files
       Enumeration<JarEntry> enEntries = jfJar.entries();
       while (enEntries.hasMoreElements()) {
         JarEntry jeEntry = enEntries.nextElement();
         if (!jeEntry.getName().endsWith("web.xml")) {
           if (!jeEntry.isDirectory()) {			
             File parent = root;
             String sFileName = jeEntry.getName();
             StringTokenizer stPathParts = new StringTokenizer(jeEntry.getName(), "/");
             if (stPathParts.countTokens() > 1) { // complex path
               // ensure that the required directories exist
               sFileName = stPathParts.nextToken();
               
               while(stPathParts.hasMoreTokens()) {
                 // previous token was not the last, so it
                 // must be a directory
                 parent = new File(parent, sFileName);
                 if (!parent.exists()) {
                   parent.mkdir();
                 }
                 
                 sFileName = stPathParts.nextToken();
               } // next token
             } // complex path
             File file = new File(parent, sFileName);
             
             // get input stream
             InputStream in = jfJar.getInputStream(jeEntry);
             
             try {
               // get output stream
               FileOutputStream out = new FileOutputStream(file);
               
               // pump data from one stream to the other
               byte[] buffer = new byte[1024];
               int bytesRead = in.read(buffer);
               while(bytesRead >= 0) {
                 out.write(buffer, 0, bytesRead);
                 bytesRead = in.read(buffer);
               } // next chunk of data
               
               out.close();
               System.out.println(file.getPath());
             } catch (IOException x) {
               System.out.println("Could not unpack " + file.getPath() + " : " + x.getMessage());
             } finally {
               in.close();
             }
             
           } // not a directory
         } // not web.xml
       } // next entry
       System.out.println("Upgrade complete.");
       System.exit(0);
     } catch (IOException x) {
       System.err.println("Could not open containing jar file: " + x);
       System.exit(4);
       return;
     } catch (URISyntaxException x) {
       System.err.println("Invalid inferred URI: " + sFileUri);
       System.exit(5);
       return;
     }
   }
} // end of class Upgrade
