package sk.umb.bachelor.degree.posture_corrector;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;


@Entity(indices = {@Index(value = {"date"}, unique = true)})
public class DayStatistic {
    public DayStatistic()
    {}
    @Ignore
    public DayStatistic(LocalDate date) {
        this.date = date;
    }

    @NonNull
    @PrimaryKey(autoGenerate = true)
    public int id;
    public LocalDate date;
    public int usageDuration;
    public int hunchedPostureDuration;
    public int hunchedCount;
}
