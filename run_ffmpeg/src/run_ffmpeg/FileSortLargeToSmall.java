package run_ffmpeg;

import java.io.File;
import java.util.Comparator;

public class FileSortLargeToSmall implements Comparator< File >
{
	@Override
	public int compare( File lhs, File rhs )
	{
		if ( lhs.length() > rhs.length() )
		{
			return -1;
		}
		else if (lhs.length() < rhs.length() )
		{
			return 1;
		}
		else
		{
			return 0;
		}
	}
}
