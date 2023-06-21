package sk.umb.bachelor.degree.posture_corrector;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface DayStatisticDao {
    @Query("SELECT * FROM daystatistic")
    List<DayStatistic> getAll();

    @Insert
    void insert(DayStatistic statistic);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(DayStatistic statistic);

    @Query("SELECT * FROM daystatistic WHERE date = :date")
    DayStatistic getByDate(LocalDate date);

    default DayStatistic getOrCreateToday()
    {
        DayStatistic statistic = this.getByDate(LocalDate.now());
        if (statistic == null) {
            statistic = new DayStatistic(LocalDate.now());
        }
        return statistic;
    }
}
