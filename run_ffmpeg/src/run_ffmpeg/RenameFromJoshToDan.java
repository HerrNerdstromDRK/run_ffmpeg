package run_ffmpeg;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.api.ArtworkApi;
import org.openapitools.client.api.SeriesApi;
import org.openapitools.client.auth.HttpBearerAuth;

import org.openapitools.client.model.GetArtworkBase200Response;
import org.openapitools.client.model.GetSeriesBase200Response;

/**
 * The purpose of this class is to rename files from Josh's naming convention to mine.
 * His naming is like:
 *  TV: TV Show Name.S01E01.mp4
 * My naming convention is like:
 *  TV: TV Show Name - S01E01 - Episode Title.mp4
 */
public class RenameFromJoshToDan
{
	// THETVDB API key
	protected final String apiKey = "915b0698-6ce6-4a44-871e-0cb6f8dcb62c" ;
	protected String apiToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhZ2UiOiIiLCJhcGlrZXkiOiI5MTViMDY5OC02Y2U2LTRhNDQtODcxZS0wY2I2ZjhkY2I2MmMiLCJjb21tdW5pdHlfc3VwcG9ydGVkIjpmYWxzZSwiZXhwIjoxNzM5MDk3NjI0LCJnZW5kZXIiOiIiLCJoaXRzX3Blcl9kYXkiOjEwMDAwMDAwMCwiaGl0c19wZXJfbW9udGgiOjEwMDAwMDAwMCwiaWQiOiIyNzE0OTkxIiwiaXNfbW9kIjpmYWxzZSwiaXNfc3lzdGVtX2tleSI6ZmFsc2UsImlzX3RydXN0ZWQiOmZhbHNlLCJwaW4iOiJzdHJpbmciLCJyb2xlcyI6W10sInRlbmFudCI6InR2ZGIiLCJ1dWlkIjoiIn0.SBaIRzoJCDxaZHwqcObGKq_dw_FxZacSeiEk8jJz6JsqmURSnsHz4owxklS8ZuwcaElwpI1UH8daxf5n56Vv3NkepgLtWqltlGc-IrvnvqsihnEf2qrDI9Zw1m0xS2_QLyDsvVLAc9ZfDG2o8r4x0P0I00iWCsbuxJPJz_bSHDrv7nnX4inGFlWPQtS1O6BNh66WozP_Ny3PAL4EvYjIOz89_OSJD8uNIBYLa3CYgvFieq4jiFK87hyrb_fd8qN7PB7idYUx63Gwl6mY4vKv--pElF7ekPB_BB-K-J935uRH1qqAnvdMnTX31cwWaPQUnX_LudLMwUv1K25qnFReNjHBVnoslQEr1khEK4tVxISMAsZaC_JatoaRiSPBJQc3KUOK-ZC16tRZtML5qVnVhj5AtWbfF2UX6GPFxJRFwHMES7i7C1Pxl40H2LxRmYXYpYELgkbXhxn0JDYvUKutkF_ezjp0QLyLzieyw_geASD2cn5feTI7gmBFnUAAVHEyLVMRb4NzYDWUJUnT57y8rd7lnGmOfJ6reDdI_8n_rRK708_eIvWZYQMV3j5RTjiXPnF01lCmb5CWNiRWnTnrynE9zxoyhsZHpIddewGXKUuuWgFk1l3mwcJx_-5AyqP3TLE55_OxbV9sUmdksRvi6V_8IkLkZhCB5Empmcq2D2w" ;
	protected final String theTVDBBaseURI = "https://api4.thetvdb.com/v4" ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_rename_from_josh_to_dan.txt" ;

	protected final String[] directories = {
			"\\\\skywalker\\usbshare1-2\\TV"
	} ;
	protected final String[] extensions = {
			"mp4"
	} ;

	public static void main( String[] args )
	{
		ApiClient defaultClient = Configuration.getDefaultApiClient();
		defaultClient.setBasePath("https://api4.thetvdb.com/v4");

		// Configure HTTP bearer authorization: bearerAuth
		HttpBearerAuth bearerAuth = (HttpBearerAuth) defaultClient.getAuthentication("bearerAuth");
		bearerAuth.setBearerToken("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhZ2UiOiIiLCJhcGlrZXkiOiI5MTViMDY5OC02Y2U2LTRhNDQtODcxZS0wY2I2ZjhkY2I2MmMiLCJjb21tdW5pdHlfc3VwcG9ydGVkIjpmYWxzZSwiZXhwIjoxNzM5MDk3NjI0LCJnZW5kZXIiOiIiLCJoaXRzX3Blcl9kYXkiOjEwMDAwMDAwMCwiaGl0c19wZXJfbW9udGgiOjEwMDAwMDAwMCwiaWQiOiIyNzE0OTkxIiwiaXNfbW9kIjpmYWxzZSwiaXNfc3lzdGVtX2tleSI6ZmFsc2UsImlzX3RydXN0ZWQiOmZhbHNlLCJwaW4iOiJzdHJpbmciLCJyb2xlcyI6W10sInRlbmFudCI6InR2ZGIiLCJ1dWlkIjoiIn0.SBaIRzoJCDxaZHwqcObGKq_dw_FxZacSeiEk8jJz6JsqmURSnsHz4owxklS8ZuwcaElwpI1UH8daxf5n56Vv3NkepgLtWqltlGc-IrvnvqsihnEf2qrDI9Zw1m0xS2_QLyDsvVLAc9ZfDG2o8r4x0P0I00iWCsbuxJPJz_bSHDrv7nnX4inGFlWPQtS1O6BNh66WozP_Ny3PAL4EvYjIOz89_OSJD8uNIBYLa3CYgvFieq4jiFK87hyrb_fd8qN7PB7idYUx63Gwl6mY4vKv--pElF7ekPB_BB-K-J935uRH1qqAnvdMnTX31cwWaPQUnX_LudLMwUv1K25qnFReNjHBVnoslQEr1khEK4tVxISMAsZaC_JatoaRiSPBJQc3KUOK-ZC16tRZtML5qVnVhj5AtWbfF2UX6GPFxJRFwHMES7i7C1Pxl40H2LxRmYXYpYELgkbXhxn0JDYvUKutkF_ezjp0QLyLzieyw_geASD2cn5feTI7gmBFnUAAVHEyLVMRb4NzYDWUJUnT57y8rd7lnGmOfJ6reDdI_8n_rRK708_eIvWZYQMV3j5RTjiXPnF01lCmb5CWNiRWnTnrynE9zxoyhsZHpIddewGXKUuuWgFk1l3mwcJx_-5AyqP3TLE55_OxbV9sUmdksRvi6V_8IkLkZhCB5Empmcq2D2w");

		SeriesApi apiInstance = new SeriesApi( defaultClient ) ;
		BigDecimal id = new BigDecimal(280619); // BigDecimal | id
		try {
			GetSeriesBase200Response result = apiInstance.getSeriesBase( id ) ;
			System.out.println(result);
		} catch (ApiException e) {
			System.err.println("Exception when calling ArtworkApi#getArtworkBase");
			System.err.println("Status code: " + e.getCode());
			System.err.println("Reason: " + e.getResponseBody());
			System.err.println("Response headers: " + e.getResponseHeaders());
			e.printStackTrace();
		}

		//		(new RenameFromJoshToDan()).run() ;
	}

	public RenameFromJoshToDan()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public void run()
	{
		testGSON() ;
	}

	public void testGSON()
	{

	}

	protected void changeFileNames()
	{		
		final List< File > matchingFiles = new ArrayList< File >() ;
		for( String directory : directories )
		{
			log.info( "Scanning directory: " + directory ) ;
			matchingFiles.addAll( common.getFilesInDirectoryByExtension( directory, extensions ) ) ;
		}
		log.info( "Found " + matchingFiles.size() + " matching file(s)" ) ;

		//		log.info( "Found matching files: " ) ;
		for( File theFile : matchingFiles )
		{
			//			log.info( theFile.getAbsolutePath() ) ;
			boolean isTVShow = false ;
			if( theFile.getAbsolutePath().contains( "Season" ) )
			{
				isTVShow = true ;
			}

			final String oldFileName = theFile.getName() ;
			final String newFileName = isTVShow ? fixTVShowFileName( oldFileName ) : fixMovieFileName( oldFileName ) ;
		}
	}

	public String fixTVShowFileName( final String oldFileName )
	{
		//		log.info( "Fixing file name: " + oldFileName ) ;
		String newFileName = oldFileName ;
		newFileName = newFileName.replace( ",", "" ) ;
		newFileName = newFileName.replace( "P.I.", "PI." ) ;

		final String[] tokens = newFileName.split( "\\." ) ;
		//		log.info( "oldFileName: " + oldFileName + ", tokens: " + Arrays.toString( tokens ) ) ;

		if( tokens.length != 3 )
		{
			log.warning( "Invalid number of tokens (" + tokens.length + ") for file name: " + oldFileName ) ;
			return oldFileName ;
		}
		final String oldTVShowName = tokens[ 0 ] ;
		final String oldSeasonAndEpisodeName = tokens[ 1 ] ;
		final String extension = tokens[ 2 ] ;

		// Fix instances like "M+A+S+H"
		String newTVShowName = oldTVShowName.replace( "+", "-" ) ;

		// Fix dash instances "Star Trek- Voyager"
		newTVShowName = newTVShowName.replace( "-", "" ) ;

		// Remove " Classic" from "MacGyver Classic"
		newTVShowName = newTVShowName.replace( "MacGyver Classic", "MacGyver" ) ;

		// Remove double spaces
		newTVShowName = newTVShowName.replace( "  ", " " ) ;

		String newSeasonAndEpisodeName = oldSeasonAndEpisodeName ;
		String newTVEpisodeName = "TEST" ;

		newFileName = newTVShowName + " - " + newSeasonAndEpisodeName + " - " + newTVEpisodeName + "." + extension ;
		log.info( "Transform: " + oldFileName + ": " + newFileName ) ;
		return newFileName ;
	}

	public String fixMovieFileName( final String oldFileName )
	{
		return oldFileName ;
	}

}
