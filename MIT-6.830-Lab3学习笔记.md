

# MIT-6.830-Lab3

>  SimpleDB Lab3 概述：
>
> 本实验将实现一个查询优化器，主要任务包括实现一个选择性估计框架和基于代价的优化器（CBO）
> 
> 1. 实现`TableStats`类中的方法，从而使它能够使用 histograms 直方图估计 filter 算子和 scan 算子的代价
> 2. 实现`JoinOptimizer`类中的方法，从而估计 join 算子的代价
> 3. 在`JoinOptimizer`类中添加`orderJoins`方法，此方法能够为一系列 joins 生成最佳排序（使用 Selinger 算法）

## 1 优化器概述

回忆 CBO 的主要思想：

- 使用 tables 的统计信息来估计不同查询计划的代价。通常，计划的代价与 join 和 selection 的中间结果表的基数（由其生成的元组数量）以及 filter 和连接谓词的代价有关
- 使用这些统计数据以最佳方式对连接和选择进行排序，并从多个备选方案中选择连接算法的最佳实现

### 1.1 优化器总体架构

<img src="https://guanghuihuang-1315055500.cos.ap-guangzhou.myqcloud.com/%E5%AD%A6%E4%B9%A0%E7%AC%94%E8%AE%B0/%E6%95%B0%E6%8D%AE%E5%BA%93/mit6.830/05.png" alt="image" style="zoom: 50%;" />

上图中双线边框的组件是本实验需要实现的部分。`Parser.java`构造了一系列表的统计信息（存放在 `statsMap`中），然后监听查询请求，并调用`parseQuery`方法处理请求；`parseQuery`方法首先将查询解析为`LogicalPlan`对象，然后将其作为参数调用`physicalPlan`方法，`physicalPlan`方法会返回一个能实际执行查询的`DBIterator`对象。

在接下来的 exercises 中，将实现一系列方法帮助`physicalPlan`方法选择最优的 plan。

### 1.2 统计信息估计

事实上，估计 plan 的代价非常棘手，本实验仅关注 joins 序列和表 access method 的代价，不关心 access method 的选择（只考虑 tablescans）以及其他算子的代价（比如 aggregates）







## Exercise1: IntHistogram











## Exercise2: TableStats









## Exercise3: Join Cardinality

