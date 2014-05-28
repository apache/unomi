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
        // Remember, the underlying tree data can be o any structure...
        var myTree = {
            root1: ['node1', 'node2', 'node3'],
            root2: ['node4', 'node5'],
            node1: ['node10', 'node11'],
            node3: ['node30', 'node31', 'node32'],
            node5: ['node50', 'node51'],
            node51: ['node510', 'node511']
        };


        $scope.myTreeHandle = {
            atSelectTreeNode: function (treeNode) {
                $scope.selected = treeNode;
            },
            getChildren: function (parent) {
                if (parent === undefined) {
                    // this means "get all root nodes"
                    return [
                        {name: 'root1'},
                        {name: 'root2'}
                    ];
                } else {
                    return myTree[parent.name] && myTree[parent.name].map(function (e) {
                        return {name: e};
                    }) || undefined;
                }
            },
            hasChildren: function (treeNode) {
                if (treeNode === undefined) {
                    return true;
                } else {
                    return myTree[treeNode.name] !== undefined;
                }
            }
        };
    });