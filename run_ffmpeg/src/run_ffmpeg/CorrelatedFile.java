package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Structure that stores information about a file attached to a movie or tv show.
 * The idea is that we need to record and match each mkv file with its corresponding
 *  mp4 file.
 * Instances of this class are intended to be stored in the database.
 * @author Dan
 */
public class CorrelatedFile implements Comparable< CorrelatedFile >
{
	/// Name of the file in question, without extension.
	/// For example: "Making Of-behindthescenes"
	public String fileName = null ;
	
	/// Record if an mkv or mp4 file is missing for use in the database
	/// and presentation to the user.
	/// These variables are only updated in the normalizeMKVAndMP4Files() method.
	public boolean missingMKVFile = false ;
	public boolean missingMP4File = false ;

	/// These next two record the mkv and mp4 files names and are populated
	/// exclusively when the database needs to record this CorrelatedFile.
	public List< String > mkvFilesByName = new ArrayList< String >() ;
	public List< String > mp4FilesByName = new ArrayList< String >() ;

	private transient List< FFmpegProbeResult > mkvFilesByProbe = new ArrayList< FFmpegProbeResult >() ;
	private transient List< FFmpegProbeResult > mp4FilesByProbe = new ArrayList< FFmpegProbeResult >() ;

	public CorrelatedFile( String fileName )
	{
		this.fileName = fileName ;
	}
	
	public CorrelatedFile()
	{}

	/**
	 * Return true if this CorrelatedFile is missing a file.
	 * This will mean that the number of mkv files is different from the
	 *  number of mp4 files.
	 * @return
	 */
	public boolean isMissingFile()
	{
		return (mkvFilesByProbe.size() != mp4FilesByProbe.size()) ;
	}
	
	public String toString()
	{
		String retMe = "{fileName:" + fileName
				+ "," ;
		for( String probeResult : mkvFilesByName )
		{
			retMe += probeResult + "," ;
		}
		for( String probeResult : mp4FilesByName )
		{
			retMe += probeResult + "," ;
		}
		retMe += "}" ;
		return retMe ;
	}

	/**
	 * Strip most of the information about a file's absolute path.
	 * This is targeted to how I currently have the workflow setup, wherein
	 *  each file used by this suite of tools includes the full path name:
	 *   \\yoda\\MKV_Archive1\\Movies\\Transformers (2002)\\Transformers (2002).mkv
	 * That string is too long to present in a web interface side by side with other things,
	 *  so this method will shorten to something like "MKV_1\\Transformers (2002).mkv"
	 * @param inputName
	 * @return
	 */
	public static String shortenFileName( final String inputName )
	{
		String retMe = "" ;
		StringTokenizer tokens = new StringTokenizer( inputName, "\\" ) ;
		
		// Walk through the tokens to build the shortened file name
		// Keep only "MKV_#" and the actual file name.
		while( tokens.hasMoreTokens() )
		{
			String nextToken = tokens.nextToken() ;
			if( nextToken.contains( "MKV_" ) || nextToken.contains( "MP4" ) )
			{
				retMe += nextToken.replace( "Archive", "" ) + "\\" ;
			}
			else if( nextToken.contains( ".mkv" ) || nextToken.contains( ".mp4" ) )
			{
				retMe += nextToken ;
			}
		}
		return retMe ;
	}

	/**
	 * Record an mkv file for this correlated file.
	 * @param theMKVFileProbeResult
	 */
	public void addMKVFile( FFmpegProbeResult theMKVFileProbeResult )
	{
		mkvFilesByProbe.add( theMKVFileProbeResult ) ;
		final String shortenedFileName = shortenFileName( theMKVFileProbeResult.getFilename() ) ;
		mkvFilesByName.add( shortenedFileName ) ;
	}

	/**
	 * Record an mp4 file for this correlated file.
	 * @param theMP4FileProbeResult
	 */
	public void addMP4File( FFmpegProbeResult theMP4FileProbeResult )
	{
		mp4FilesByProbe.add( theMP4FileProbeResult ) ;
		final String shortenedFileName = shortenFileName( theMP4FileProbeResult.getFilename() ) ;
		mp4FilesByName.add( shortenedFileName ) ;
	}

	/**
	 * This method will fill any empty mkv or mp4 files with "(none)" to ensure the
	 * number of mkv and mp4 files matches for presentation.
	 */
	public void normalizeMKVAndMP4Files()
	{
		missingMKVFile = false ;
		missingMP4File = false ;
		
		while( mkvFilesByName.size() < mp4FilesByName.size() )
		{
			missingMKVFile = true ;
			mkvFilesByName.add( "(none)" ) ;
		}
		while( mp4FilesByName.size() < mkvFilesByName.size() )
		{
			missingMP4File = true ;
			mp4FilesByName.add( "(none)" ) ;
		}
	}

	@Override
	public int compareTo( CorrelatedFile rhs )
	{
		return fileName.compareTo( rhs.fileName ) ;
	}

}
