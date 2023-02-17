package run_ffmpeg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;

import com.google.common.collect.ImmutableList;

/**
 * Store the data returned from an ffprobe invocation.
 * @author Dan
 */
public class FFmpegProbeResult
{
	/// This object's id in the database.
	private ObjectId _id ;
	
	/// The object's id as a String
//	private transient String idString = null ;
	
	/// The file being probed.
	private String filename = null ;
	
	/// The time, in ms since 1-Jan-1970, of this probe
	private long probeTime = 0 ;
	
	/// Size of the file in bytes
	private long size = 0 ;
	
	/// Store any error state
	public FFmpegError error = null ;

	/// Store the format of the file
	public FFmpegFormat format = null ;

	/// Store the streams
	public List< FFmpegStream > streams = null ;

	/// The chapters
	public List< FFmpegChapter > chapters = null ;

	public FFmpegError getError() {
		return error;
	}

	public boolean hasError() {
		return error != null;
	}

	public FFmpegFormat getFormat() {
		return format;
	}

	/**
	 * Return a copy of the streams.
	 * @return
	 */
	public List< FFmpegStream > getStreams()
	{
		if (streams == null) return Collections.emptyList();
		return ImmutableList.copyOf( streams );
	}

	/**
	 * Return a copy of each stream matching the given codec_type.
	 * @param searchType
	 * @return
	 */
	public List< FFmpegStream > getStreamsByCodecType( final String searchType )
	{
		List< FFmpegStream > returnMe = new ArrayList< FFmpegStream >() ;
		for( FFmpegStream theInputStream : getStreams() )
		{
			if( theInputStream.codec_type.equalsIgnoreCase(searchType) )
			{
				returnMe.add( theInputStream ) ;
			}
		}		  
		return returnMe ;
	}

	/**
	 * Return a copy of the chapters.
	 * @return
	 */
	public List< FFmpegChapter > getChapters()
	{
		if (chapters == null) return Collections.emptyList();
		return ImmutableList.copyOf(chapters);
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public long getProbeTime() {
		return probeTime;
	}

	public void setProbeTime(long probeTime) {
		this.probeTime = probeTime;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}
	
//	public String getIDString()
//	{
//		if( null == idString )
//		{
//			idString = _id.toString() ;
//		}
//		return idString ;
//	}
	
	public String toString()
	{
		String retMe = "{filename: " + getFilename()
		+ ",streams.size: " + streams.size()
//		+ ",chapters.size: " + chapters.size()
		+ "}" ;
		return retMe ;
	}
	
}
