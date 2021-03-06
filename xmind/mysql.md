## MySQL中的字符集和比较规则

### 字符集

```mysql
SHOW CHARSET LIKE 'utf8_%';
```

| 字符集                                         | 编码长度                                           |
| ---------------------------------------------- | -------------------------------------------------- |
| ASCII（128个字符）                             | 1字节                                              |
| ISO 8859-1（256个字符，又叫latin1）            | 1字节                                              |
| GB2312（收录6763个汉字，兼容ASCII）            | 字符在ASCII中采用1字节，否则2字节                  |
| GBK（对GB2312进行扩充，兼容GB2312）            | 与GB2312相同                                       |
| Unicode（兼容`ASCII`字符集，采用变长编码方式） | UTF-8:：1-4个字节，UTF-16：2或4字节，UTF-32：4字节 |

> MySQL中的`utf8`和`utf8mb4`字符集区别在于前者是1-3字符（阉割），后者是1-4字符。

### 比较规则

```mysql
SHOW COLLATION LIKE 'utf8_%';
```

|  后缀  |         英文         |    不区分重音    |
| :----: | :------------------: | :--------------: |
| `_ai`  | `accent insensitive` |    不区分重音    |
| `_as`  |  `accent sensitive`  |     区分重音     |
| `_ci`  |  `case insensitive`  |   不区分大小写   |
| `_cs`  |   `case sensitive`   |    区分大小写    |
| `_bin` |       `binary`       | 以二进制方式比较 |

> MySQL中utf8默认的比较规则就是`utf8_general_ci`。

### 字符集与比较规则的级别

```mysql
# [服务器级别]
SHOW VARIABLES LIKE 'character_set_server';
SHOW VARIABLES LIKE 'collation_server';
# [创建或修改数据库比较规则]
CREATE[ALTER] DATABASE [database_name] CHARACTER SET utf8 COLLATE utf8_general_ci;
# [数据库级别]
USE [database_name];
SHOW VARIABLES LIKE 'character_set_database';
SHOW VARIABLES LIKE 'collation_database';
# [表级别] 如果表不设置字符集和比较规则，默认继承数据库的配置
CREATE[ALTER] TABLE unicode(name VARCHAR(10)) CHARACTER SET utf8 COLLATE utf8_general_ci;
# [表级别] 查看表的字符集和编码规则
SHOW TABLE STATUS FROM unicode;

# [创建列的字符集和比较规则] 不设置默认读取表的配置
CREATE TABLE line(
	name VARCHAR(10) CHARACTER SET utf8 COLLATE utf8_general_ci,
    age INT(16)
)
ALTER TABLE [table_name] MODIFY [column] VARCHAR CHARACTER SET latin1 COLLATE latin1_general_cs;
```

> 无论是只修改字符集或比较规则，未设置的一方都会自动的改为与修改一方对应的配置。

### MySQL中字符集的转换

![](https://image.leejay.top/FrSGcCPcK8QLB6_kDsuBDygM8shm)

> 可以使用`SET NAMES utf8;`一起设置如上三个参数配置。

---

## MySQL索引

### 概念

#### B+树

B+树最上层的是根节点，还包含存放目录记录的数据页（目录页）非叶子节点和存放用户记录的数据页（用户记录页）叶子节点。

![](https://image.leejay.top/Fk4MgNHjS271O1t7aTzGFQ8mwf6A)

> 目录页和用户记录页的区别在于前者中的目录数据的`record_type = 1`，后者的用户记录数据的`record_type  = 0`。目录页中映射的索引列的值都是对应用户记录页中索引列的最小值。

#### 聚簇索引

根据`主键值的大小(从小到大)`进行目录页（双向链表）和记录页（单向链表）的排序，B+树的叶子节点存储的是`完整的用户数据（包括隐藏列）。`InnoDB存储引擎会自动的为我们创建聚簇索引。

#### 二级索引

根据`指定列的大小（从小到大）`进行目录页（双向链表）和记录页（单向链表）的排序， B+树的叶子节点存储的是``指定列的值 + 主键值`，相比聚簇索引，二级索引第一次查询得到主键值后，会进行第二次`回表查询`操作。

> 二级索引在目录页中存放的记录是`指定列 + 主键值 + 目录页码（保证唯一性）`，这样能够在指定列出现相同值时定位到目录页（叶子节点）。
>
> 回表还是全表扫描，这个是由回表的代价决定的，如果第一次查询二级索引（顺序IO）有90%的数据需要回表查询(随机IO)，那么不如直接进行全表扫描（这个是由`查询优化器`决定的）。
>
> 所以更推荐`覆盖索引`，即查询的列表中只包含索引列。
>
> ```mysql
> # 这样就不需要回表查询了，因为查询的字段在二级索引的叶子节点中都存在
> SELECT name, birthday, phone_number FROM person_info ORDER BY name, birthday, phone_number;
> ```

#### 联合索引

本质上也是一个二级索引，现根据A列的大小进行排序，在A列的值相等的情况下根据B列的值进行排序。非叶子节点（目录页）由`A列 + B列 + 主键 + 页码`组成，同时叶子节点的用户记录由`A列 + B列 + 主键列`组成。

#### 注意事项

- 每当表创建一个B+树时，都会为这个索引创建一个根节点页面，随着表中插入数据，会先把数据插入根节点中，随着数据量增多，会复制数据到新的页中，并升级为目录页。此过程中根节点地址是不会变的，变的只是角色。
- 一个页面至少存储两条数据。

#### 索引的查询

```mysql
# 创建表时添加索引
CREATE TABLE demo(
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(10),
  PRIMARY KEY(id),
  UNIQUE INDEX idx_name(name) # 创建唯一索引
)
# 修改表添加索引
ALTER TABLE demo DROP INDEX idx_name;
ALTER TABLE demo ADD FULLTEXT INDEX f_name_idx(name); 
```

---

### 索引适用条件

#### 全值匹配

搜索条件中的列和索引列一致的话，这种情况就称为全值匹配。即使我们不按照联合索引创建的列的顺序查询，也是会走联合索引的（查询优化器）。

#### 最左匹配

在全值匹配的基础上，查询可以不用包含全部的列，只需要包含一个或多个最左边的列就可以（最左匹配原则）。我们按照`a-b-c`的顺序创建了联合索引，那么`a、a-b、a-b-c、a-c（a生效）`查询方式都是可以走联合索引的。但`b-c`是不生效的。

> 最左匹配原则遇到`范围查询`就会停止匹配。

#### 前缀匹配

查询的字符串列的前缀都是排好序的，那么只匹配它的前缀也是可以快速定位记录。

```mysql
SELECT * FROM person_info WHERE name LIKE 'As%'; √
SELECT * FROM person_info WHERE name LIKE '%As%'; ×
```

> 针对一些无法使用前缀匹配的字段，比如`xxx.com`的搜索，我们可以反转字符串然后基于`com%`进行前缀匹配。

#### 范围匹配

```mysql
SELECT * FROM person_info WHERE name > 'Asa' AND name < 'Barlow';
```

> 基于联合索引（此处只用到了`name`）找到第一个大于`Asa`的数据返回主键，回表查询返回给客户端。然后沿着上一步记录所在的链表继续查找，下一条二级索引记录，并判断是否符合小于`Barlow`，符合就回表查询并返回数据，重复步骤直到条件不符合。

```mysql
SELECT * FROM person_info WHERE name > 'Asa' AND name < 'Barlow' AND birthday > '1980-01-01';
```

> 基于上步的范围匹配流程得到的结果集，进行`birthday > '1980-01-01'`再匹配（但不会走联合索引）。

#### 精确匹配某一列与范围匹配另一列

```mysql
SELECT * FROM person_info WHERE name = 'Ashburn' AND birthday > '1980-01-01' AND birthday < '2000-12-31' AND phone_number > '15100000000'; ×
SELECT * FROM person_info WHERE name = 'Ashburn' AND birthday = '1980-01-01' AND phone_number > '15100000000'; √
```

> `name`等值匹配可以走联合索引，当`name相同时`都是按照`birthday`从小到大的进行排序，所以可以进行`birthday`的范围匹配，但`birthday`的范围匹配就无法保证`phone_number`从小到大排序，所以`phone_number`只能遍历结果进行筛选，无法走联合索引。

#### 排序

```mysql
SELECT * FROM person_info ORDER BY name, birthday, phone_number LIMIT 10; √
SELECT * FROM person_info ORDER BY name asc, birthday desc, phone_number asc; ×
SELECT * FROM person_info WHERE name = 'A' ORDER BY birthday, phone_number LIMIT 10; √
SELECT * FROM person_info ORDER BY name, country LIMIT 10; ×
SELECT * FROM person_info ORDER BY UPPER(name) LIMIT 10; ×
```

> 按照联合索引的顺序进行排序（默认联合索引的B+树就按照这个顺序创建）。
>
> 1. 如果联合索引中的每个列的查询顺序不一致，那么不能使用索引进行排序。
> 2. 如果排序中列包含非同一个索引的列，那么不能使用索引进行排序。
> 3. 排序列使用了复杂的表达式，如`UPPER()、REPLACE()`等。

#### 分组

```mysql
SELECT name, birthday, phone_number, COUNT(*) FROM person_info GROUP BY name, birthday, phone_number; √
```

> `group by`按照联合索引的顺序，索引也是生效的。

### 如何使用索引

- 只为用于搜索、排序、分组的列创建索引。

- 为基数大（这一列中不重复的数据，基数越大不重复的越多）的列创建索引。

- 索引列的类型尽量的小（减少磁盘占用和提升读写IO）。

- 索引字符串值的前缀，针对列的值特别长的情况（但是基于`此列的排序会走文件排序`：在内存或磁盘中排序）。

  ```mysql
  # 添加字符串前缀索引，只索引前10个字符的编码
  ALTER TABLE person_info ADD INDEX idx_name(name(10));
  ```

  > 通过前缀索引然后定位到相应前缀所在的位置，然后回表匹配完成的字符串。

- 索引列在比较表达式中单独出现（age > 2 √  age * 2 > 10  ×）。

- 主键最好自增，避免因为主键值忽大忽小带来的页分裂问题（性能损失）。

- 避免创建冗余和重复索引。

- 尽量使用索引覆盖（查询索引中的字段）进行查询，避免由回表查询变为全文搜索。

---

## MySQL的数据目录

### 数据目录的组成

```mysql
# 查看mysql的数据目录（不是安装目录，安装目录包含bin文件夹）
SHOW VARIABLES LIKE 'datadir';
```

> MySQL的数据目录下存在与`创建的数据库同名`的文件夹，文件夹内会存在`表名.frm`和`表名.ibd`两种类型的文件。
>
> 其中`表名.frm`是表结构的描述文件，`表名.ibd`存放的是表的数据（MySQL5.6.6以前的版本中所有的表记录都存放到名为ibdata1的文件中（系统表空间），之后的版本中每个表都对应着一个`表名.ibd`的文件（独立表空间）。
>
> 除此之外，数据目录下还存在MySQL服务进程文件、日志文件、SSL和RSA密钥文件等。

## MySQL系统数据库

- `mysql`

  它存储了MySQL的用户账户和权限信息，一些存储过程、事件的定义信息，一些运行过程中产生的日志信息，一些帮助信息以及时区信息等。

- `information_schema`

  这个数据库保存着MySQL服务器维护的所有其他数据库的信息，比如有哪些表、哪些视图、哪些触发器、哪些列、哪些索引。这些信息并不是真实的用户数据，而是一些描述性信息，有时候也称之为元数据。

- `performance_schema`：

  这个数据库里主要保存MySQL服务器运行过程中的一些状态信息，算是对MySQL服务器的一个性能监控。包括统计最近执行了哪些语句，在执行过程的每个阶段都花费了多长时间，内存的使用情况等等信息。

- `sys`

  这个数据库主要是通过视图的形式把`information_schema`和`performance_schema`结合起来，让程序员可以更方便的了解MySQL服务器的一些性能信息。

---

## MySQL的单表访问方法

MySQL执行查询语句的方式称为`访问方法`。对于单表来说，MySQL的单表查询方式被分为`全表扫描查询`和`索引查询`。

### 单表访问方法

![](https://image.leejay.top/FqwMONwuoy55A6NFM1ghvcBD090-)

### 单表访问的注意事项

#### 单个二级索引

`一般情况`下只能利用`单个二级索引`执行查询。

```mysql
SELECT * FROM single_table WHERE key1 = 'abc' AND key2 > 1000;
```

> MySQL查询优化器会判断使用哪个二级索引查询扫描的行数更少，选择较少的那个二级索引查询主键，回表查询后将得到的结果再根据其他的条件进行过滤。

#### 索引合并

一般情况下执行一个查询时最多只会用到单个二级索引，但使用到多个索引来完成一次查询的执行方法称之为：`index merge（索引合并）`。

- Intersection合并

某个查询可以使用多个二级索引，将二级索引查询的结果取`交集`，再回表查询。必须符合如下情况才可能会使用到Intersection合并：

1. 二级索引列都是等值匹配的情况，对于联合索引来说，在联合索引中的`每个列都必须等值匹配`，不能出现只匹配部分列的情况。

```mysql
SELECT * FROM single_table WHERE key1 = 'a' AND key3 = 'b';
```

1. 主键列可以是范围匹配。因为二级索引的列相同时会按照主键的顺序进行排序，有序的主键有助于提升取交集速度。

```mysql
# 可能会用到聚簇索引和二级索引合并，因为key1作为二级索引叶子节点中是包含主键的，可以直接二级索引查询后再
# 进行主键匹配，然后回表。这里主键的搜索条件只是从别的二级索引得到的结果集中过滤记录。是不是等值不重要
SELECT * FROM single_table WHERE id > 100 AND key1 = 'a'; 
```

> 按照有序的主键回表取记录有个专有名词叫：Rowid Ordered Retrieval，简称ROR。

上述的条件一二是发生Intersection合并的必要条件，但不是充分条件，也就是说即使情况一、情况二成立，也不一定发生`Intersection`索引合并，这得看优化器的心情。优化器只有在单独根据搜索条件从某个二级索引中获取的记录数太多，导致回表开销太大，而通过`Intersection`索引合并后需要回表的记录数大大减少时才会使用`Intersection`索引合并。

如果多个列不需要单独使用的话还是`推荐使用联合索引替代索引合并`，少读一颗B+树的同时也不同合并结果。

- Union合并

某个查询可以使用多个二级索引，将二级索引查询的结果取`并集`，再回表查询。必须符合如下情况才可能会使用到Union合并：

1. 二级索引列都是等值匹配的情况，对于联合索引来说，在联合索引中的`每个列都必须等值匹配`，不能出现只匹配部分列的情况。
2. 主键列可以是范围匹配
3. 使用`Intersection`索引合并的搜索条件

```mysql
SELECT * FROM single_table WHERE key_part1 = 'a' AND key_part2 = 'b' AND key_part3 = 'c' OR (key1 = 'a' AND key3 = 'b');
```

> 1. 先按照`key1 = 'a' AND key3 = 'b'`使用`Intersection索引合并`的方式得到一个主键集合。
> 2. 再通过ref的访问方法获取`key_part1 = 'a' AND key_part2 = 'b' AND key_part3 = 'c'`的主键集合。
> 3. 采用`Union`索引合并的方式把上述两个主键集合取并集，然后进行回表操作返回数据。

- Sort-Union合并

按照二级索引记录的主键值进行排序，之后按照`Union`索引合并方式执行的方式称之为`Sort-Union`索引合并。比单纯的`Union`索引合并多了一步对二级索引记录的主键值排序的过程。

> Intersection合并适合的是从二级索引中获取的记录数太多，导致回表开销太大而出现的，如果存在Sort-Intersection合并，那么对大量数据进行排序是非常耗时的，所以不存在Sort-Intersection合并。

---

