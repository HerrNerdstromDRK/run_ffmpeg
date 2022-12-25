package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilePathInfo {

	public static void main( String[] args )
	{
		List< String > fileNames = new ArrayList< >() ;
		fileNames.add( "\\\\yoda\\Backup\\Movies\\Transformers (2007)\\Transformers (2007).mkv" ) ;
		fileNames.add( "\\\\yoda\\Backup\\Movies\\Transformers (2007)" ) ;

		try
		{
//			Pattern yearPattern = Pattern.compile( "^[1-9]\\d*$");
			Pattern yearPattern = Pattern.compile( "\\b\\d{4}\\b");
			String[] testYearStrings = {
					"Name (1999)", // Pass
					"1999", // Fail
					"Name 1999", // Fail
					"(2)", // Fail
					"Name - Name2 (2000)", // Pass
					"Name (123)", // Fail
					"Name (2022)" // Pass

			};
			for( String testYear : testYearStrings )
			{
				Matcher theMatcher = yearPattern.matcher( testYear ) ;
				System.out.println( "regex> Input: \"" + testYear + "\": matches(): " + theMatcher.matches() ) ;
			}

			for( String fileName : fileNames )
			{
				File theFile = new File( fileName ) ;
				System.out.println( "main> File: " + theFile ) ;
				System.out.println( "main> length(): " + theFile.length() ) ;
				System.out.println( "main> getCanonicalPath(): " + theFile.getCanonicalPath() ) ;
				System.out.println( "main> getAbsolutePath(): " + theFile.getAbsolutePath() ) ;
				System.out.println( "main> getName(): " + theFile.getName() ) ;
				System.out.println( "main> getPath(): " + theFile.getPath() ) ;
				System.out.println( "main> getParent(): " + theFile.getParent() ) ;
				System.out.println( "main> getParentFile().getName(): " + theFile.getParentFile().getName() ) ;
				System.out.println( "main> getParentFile().getParent(): " + theFile.getParentFile().getParent() ) ;
				System.out.println( "main> getPath(): " + theFile.getPath() ) ;
			}
		}
		catch( Exception theException )
		{
			System.out.println( "main> Exception: " + theException ) ;
			theException.printStackTrace() ;
		}
	} //main()

}
