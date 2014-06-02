angular.module('manageSegments', ['ui.bootstrap', 'twinColTreeNav', 'ngDragDrop'])

    .controller('ManageSegmentsCtrl', function ($scope) {

        $scope.dropSuccessHandler = function ($event, index, array) {
            alert("index=" + index);
            // array.splice(index,1);
        };

        $scope.onDrop = function ($event, $data, array) {
            alert("data=" + $data);
            // array.push($data);
        };

    })

    .controller('MillerColumnsCtrl', function ($scope) {

        var conditionTree = {
            name : 'root',
            children: [
                {
                    name: 'Demographic'
                },
                {
                    name: 'Geographic'
                },
                {
                    name: 'Logical',
                    children: [
                        {
                            name: 'AND',
                            type: 'subconditions'
                        },
                        {
                            name: 'OR',
                            type: 'subconditions'
                        },
                        {
                            name: 'NOT',
                            type: 'subconditions'
                        }
                    ]
                },
                {
                    name: 'Algorithmic'
                }
            ]
        };

        $scope.conditionTreeHandle = {
            atSelectTreeNode: function (treeNode) {
                $scope.selected = treeNode;
            },
            getChildren: function (parent) {
                if (parent === undefined) {
                    // this means "get all root nodes"
                    return conditionTree.children;
                } else {
                    return parent.children;
                }
            },
            hasChildren: function (treeNode) {
                if (treeNode === undefined) {
                    return true;
                } else {
                    return treeNode.children !== undefined;
                }
            }
        };

    });